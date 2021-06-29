package ru.stm.rpc.kafkaredis.consumer;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.kafka.support.Acknowledgment;
import reactor.core.publisher.Mono;
import ru.stm.platform.BusinessError;
import ru.stm.rpc.core.RpcExpiredException;
import ru.stm.rpc.core.RpcResultCommon;
import ru.stm.rpc.kafkaredis.config.KafkaRpcConnection;
import ru.stm.rpc.types.RpcRequest;
import ru.stm.rpc.types.RpcResultType;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
abstract class AbstractRpcListener implements ApplicationListener {

    private final static AtomicLong polled = new AtomicLong(0);
    private final RpcResponseService rpcResponseService;

    private static final Object lock = new Object();
    private static volatile boolean initializedThread = false;
    private static volatile boolean initializedMetrics = false;
    private final int defaultRpcTimeout;

    private static final Map<RpcConsumerStatus, AtomicLong> metricsOverall = new ConcurrentHashMap<>();
    private static final Map<RpcConsumerStatus, AtomicLong> prevStatsAll = new ConcurrentHashMap<>();

    private final MeterRegistry meterRegistry;
    private final String namespace;
    private final KafkaRpcConnection connection;

    private static Thread statisticsThread;

    public AbstractRpcListener(RpcResponseService rpcResponseService, KafkaRpcConnection connection, MeterRegistry meterRegistry) {
        this.namespace = connection.getNamespace();
        this.rpcResponseService = rpcResponseService;
        this.defaultRpcTimeout = connection.getProps().getConsumer().getExecutionTimeout();
        this.meterRegistry = meterRegistry;
        this.connection = connection;

        synchronized (lock) {
            if (!initializedMetrics) {
                initMetricMap(metricsOverall);
                initMetricMap(prevStatsAll);
                initializedMetrics = true;
            }
        }
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ContextRefreshedEvent) {
            synchronized (lock) {
                if (!initializedThread) {
                    final long statsInterval = connection.getProps().getConsumer().getStatsInterval();
                    final boolean showCurrentState = connection.getProps().getConsumer().isShowCurrentStats();

                    startMetricsThread(showCurrentState, statsInterval);
                    initializedThread = true;
                }
            }
        }

        if (event instanceof ContextClosedEvent) {
            synchronized (lock) {
                if (initializedThread && statisticsThread.isAlive()) {
                    statisticsThread.interrupt();
                }
                initializedThread = false;
            }
        }
    }

    // thread which sent statistics (if enabled) and adding actual data to meterRegistry (prometheus)
    private void startMetricsThread(boolean showCurrentState, long statsInterval) {
        statisticsThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                metricsOverall.get(RpcConsumerStatus.RPC_SENT_OK).set(rpcResponseService.getSentResponsesOk().get());
                metricsOverall.get(RpcConsumerStatus.RPC_POLLED).set(polled.get());
                metricsOverall.get(RpcConsumerStatus.RPC_SENT_EXCEPTION).set(rpcResponseService.getSentException().get());
                metricsOverall.get(RpcConsumerStatus.RPC_SENT_FAILED).set(rpcResponseService.getSentResponsesFailed().get());

                metricsOverall.forEach((x, currentMetric) -> {
                    AtomicLong prevMetric = prevStatsAll.get(x);
                    if (prevMetric != null) {
                        meterRegistry.counter(mapRpcStatusToMetricName(x),
                                "type", "RPC", "RPC", "Consumer", "namespace", namespace).increment(currentMetric.get() - prevMetric.get());
                        prevMetric.set(currentMetric.get());
                    }
                });

                if (showCurrentState) {
                    StringBuilder s = new StringBuilder();

                    metricsOverall.forEach((m, n) -> {
                        s.append(m.name()).append(" = ").append(n.get()).append(", ");
                    });
                    if (metricsOverall.size() > 0) {
                        log.info("Consumer {} stat {}", namespace, s.toString());
                    }
                }

                try {
                    Thread.sleep(statsInterval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Metric thread is interrupted", e);
                }
            }
        });
        statisticsThread.start();
        statisticsThread.setName("RPC-Consumer-Stats-" + connection.getNamespace());
    }

    private String mapRpcStatusToMetricName(RpcConsumerStatus status) {
        return "rpc." + status.name().toLowerCase().replace("_", ".");
    }

    /**
     * Initialize metrics state of current RPC requests statuses
     *
     * @param metrics state of current RPC metrics
     */
    private void initMetricMap(Map<RpcConsumerStatus, AtomicLong> metrics) {
        metrics.put(RpcConsumerStatus.RPC_POLLED, new AtomicLong(0));
        metrics.put(RpcConsumerStatus.RPC_SENT_EXCEPTION, new AtomicLong(0));
        metrics.put(RpcConsumerStatus.RPC_SENT_FAILED, new AtomicLong(0));
        metrics.put(RpcConsumerStatus.RPC_SENT_OK, new AtomicLong(0));
    }

    protected <T> Mono<T> businessError(String code, String msg) {
        return BusinessError.businessError(code, msg);
    }

    /**
     * Execute RPC request Mono and send response
     * @param key
     * @param req
     * @param ack
     * @param execution
     * @param <N>
     * @param <T>
     * @Deprecated - use @Rpc and @RpcHandler
     */
    public <N extends RpcResultType, T extends RpcRequest> void executeAndSend(String key, String traceId, T req, Acknowledgment ack,
                                                                               RpcExecution<? extends Mono<? extends N>> execution,
                                                                               OffsetDateTime timeout) {
        polled.incrementAndGet();

        String operationName = req.getClass().getSimpleName();
        meterRegistry.counter("rpc_operation_consumer", "type", RpcConsumerStatus.RPC_POLLED.name(), "operation_name", operationName).increment();

        log.trace("Execute type {} id {}", operationName, key);

        OffsetDateTime now = OffsetDateTime.now();
        Duration operationTimeout = Duration.of(defaultRpcTimeout, ChronoUnit.MILLIS);

        // if RPC producer has a timeout - use it for consumer too
        if (timeout != null) {
            operationTimeout = Duration.between(now, timeout);

            // if RPC operation timeout expired
            if (operationTimeout.isNegative()) {
                rpcResponseService.sendExceptionally(req, key, traceId, operationName,
                        RpcExpiredException.create(operationName, key, traceId, timeout),
                        namespace, operationTimeout);
                ack.acknowledge();
                return;
            }
        }

        // Redis write timeout = operation execution timeout, which may be twice as long as the operation time
        Duration redisWriteTimeout = operationTimeout;

        // TODO: получать возможность делать timeout из @RpcHandler чтобы можно запретить дропать выполнение RPC
        Duration finalOperationTimeout = operationTimeout;

        execution.call()
                .timeout(operationTimeout)
                .doOnError(TimeoutException.class, err ->
                        log.error("Timeout during RPC request {}, req={}, key={}", operationName, req, key))
                .switchIfEmpty(businessError(RpcResultCommon.ENTITY_NOT_FOUND, RpcResultCommon.ENTITY_NOT_FOUND_TEXT))
                .subscribe((p) -> {
                    rpcResponseService.sendSuccess(req, key, traceId, operationName, p, namespace, redisWriteTimeout);
                    ack.acknowledge();
                }, (err) -> {
                    if (err instanceof BusinessError) {
                        rpcResponseService.sendErrorBusiness(req, key, traceId, operationName, (BusinessError) err, namespace, finalOperationTimeout);
                    } else {
                        rpcResponseService.sendExceptionally(req, key, traceId, operationName, err, namespace, finalOperationTimeout);
                    }
                    ack.acknowledge();
                }, () -> {

                });
    }

    @FunctionalInterface
    public interface RpcExecution<V> {
        V call();
    }

}
