package ru.stm.rpc.kafkaredis.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.util.Assert;
import org.springframework.util.concurrent.ListenableFutureCallback;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.core.scheduler.Schedulers;
import ru.stm.platform.StmExecutionError;
import ru.stm.rpc.core.*;
import ru.stm.rpc.kafkaredis.config.KafkaRedisRpcProperties;
import ru.stm.rpc.kafkaredis.config.KafkaRpcConnection;
import ru.stm.rpc.kafkaredis.serialize.RpcSerializer;
import ru.stm.rpc.services.RpcServiceRoute;
import ru.stm.rpc.types.RpcRequest;
import ru.stm.rpc.types.RpcResultType;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import static ru.stm.rpc.kafkaredis.config.KafkaRpcInternalConstants.KAFKA_REDIS_RPC_TIMEOUT;
import static ru.stm.rpc.kafkaredis.topic.InternalKafkaRpcConstants.KAFKA_GLOBAL_OPERATION_ID_SENT;

/**
 * Allows to send request to Kafka and receive response via Redis
 * <p>
 * Kafka topic is used as a namespace during processing
 */
@Slf4j
public class KafkaRedisRPCProducer extends AbstractHealthIndicator implements RpcServiceRoute<RpcCtx>, ApplicationListener<ContextRefreshedEvent> {

    private static final String RPC_REDIS_POLLING = "RPC-Redis-Polling-";

    private final KafkaTemplate kafkaTemplate;
    private final ReactiveRedisTemplate<String, ?> reactiveRedisTemplate;
    private final ObjectMapper objectMapper = RpcSerializer.getObjectMapper();
    private final KafkaRedisRpcProperties.KafkaRedisRpcItem props;

    private final ForkJoinPool rpcResultExecutorPool = new ForkJoinPool();

    private final Map<String, RpcState> currentRpc = new ConcurrentHashMap<>();

    private final Long redisPollingInterval;
    private final Long redisPollingIdleInterval;
    private final long rpcTimeout;

    private volatile Map<RpcStatus, AtomicLong> metricsCurrent = new ConcurrentHashMap<>();
    private volatile Map<RpcStatus, AtomicLong> metricsOverall = new ConcurrentHashMap<>();

    private final MeterRegistry meterRegistry;

    // thread which poll Redis
    private volatile Thread redisThread;

    private final String namespace;

    // timeout for system operation such as delete RPC GC or poll batch
    private final Duration deleteOperationsTimeout;

    private final Duration pollingOperationsTimeout;

    // true if timeout errors is larger than threshold
    private volatile boolean inFailedByTimeoutState = false;

    private volatile long prevFailedStats = 0;

    private final int timeoutFailedThreshold;

    private final Set<String> finalizerRedisDelete = new CopyOnWriteArraySet<>();

    public KafkaRedisRPCProducer(KafkaRpcConnection connection, MeterRegistry meterRegistry) {
        Assert.notNull(connection.getNamespace(), "Null namespace");

        this.kafkaTemplate = connection.getKafkaTemplate();
        this.reactiveRedisTemplate = connection.getRedisTemplate();
        this.props = connection.getProps();
        this.namespace = connection.getNamespace();
        this.rpcTimeout = connection.getProps().getProducer().getTimeout();
        this.redisPollingInterval = connection.getProps().getProducer().getRedisPolling();
        this.redisPollingIdleInterval = connection.getProps().getProducer().getRedisPolling();
        this.deleteOperationsTimeout = Duration.of(connection.getProps().getProducer().getInternalDeleteOperationTimeout(), ChronoUnit.SECONDS);
        this.pollingOperationsTimeout = Duration.of(connection.getProps().getProducer().getPollingOperationTimeout(), ChronoUnit.SECONDS);

        this.meterRegistry = meterRegistry;
        initMetricMap(metricsOverall);

        this.timeoutFailedThreshold = props.getProducer().getTimeoutFailedThreshold();

        startFinalizerThread();
        startRedisPollingThread();
    }

    /**
     * Initialize status metrics state for current RPC requests
     *
     * @param metrics current metrics state
     */
    private void initMetricMap(Map<RpcStatus, AtomicLong> metrics) {
        metrics.put(RpcStatus.CREATED, new AtomicLong(0));
        metrics.put(RpcStatus.DONE_FAILED, new AtomicLong(0));
        metrics.put(RpcStatus.DONE_OK, new AtomicLong(0));
        metrics.put(RpcStatus.FAILED_TIMEOUT, new AtomicLong(0));
        metrics.put(RpcStatus.GET_FROM_REDIS, new AtomicLong(0));
        metrics.put(RpcStatus.FAILED_TO_KAFKA, new AtomicLong(0));
        metrics.put(RpcStatus.PROCESSING, new AtomicLong(0));
        metrics.put(RpcStatus.SENT_TO_KAFKA, new AtomicLong(0));
        metrics.put(RpcStatus.TO_PROCESS_QUEUE, new AtomicLong(0));
    }

    /**
     * @param status
     * @return true - if the request status is completed and nothing more will be produced on this request
     */
    private boolean isTerminateStatus(RpcStatus status) {
        return RpcStatus.DONE_OK.equals(status) ||
                RpcStatus.FAILED_TIMEOUT.equals(status) ||
                RpcStatus.FAILED_TO_KAFKA.equals(status) ||
                RpcStatus.DONE_FAILED.equals(status);
    }

    private String mapRpcStatusToMetricName(RpcStatus status) {
        return "rpc." + status.name().toLowerCase().replace("_", ".");
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        startStatsThread();
    }

    // thread for completed requests statistics update and for related data remove
    private void startFinalizerThread() {
        final long cleanerFinalizerInterval = props.getProducer().getCleanerFinalizerInterval();

        Thread cleanerThread = new Thread(() -> {

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Map<RpcStatus, AtomicLong> statsTemp = new ConcurrentHashMap<>();
                    initMetricMap(statsTemp);

                    List<String> toDelete = Collections.synchronizedList(new ArrayList<>(currentRpc.size() / 2));

                    OffsetDateTime terminateStaleRpc = OffsetDateTime.now().minusSeconds(props.getProducer().getFinalizerStaleSec());

                    currentRpc.entrySet().parallelStream().forEach(x -> {
                        String key = x.getKey();
                        RpcState value = x.getValue();
                        RpcStatus status = value.getStatus();
                        statsTemp.get(status).incrementAndGet();

                        if (isTerminateStatus(status)) {
                            toDelete.add(key);
                            metricsOverall.get(status).incrementAndGet();
                        } else {
                            if (terminateStaleRpc.isAfter(x.getValue().getFinalTimeout())) {
                                log.error("Stale RPC operationId={} type={} status={}", x.getKey(), x.getValue().getReturnClass().getSimpleName(),
                                        x.getValue().getStatus());
                                toDelete.add(key);
                            }
                        }
                    });

                    metricsCurrent = statsTemp;

                    toDelete.forEach(currentRpc::remove);

                    int currentRpcSize = currentRpc.keySet().size();
                    if (currentRpcSize > props.getLoggingThreshold()) {
                        log.warn("CurrentRpc {} is larger than STATS_CURRENT_RPC_THRESHOLD", currentRpcSize);
                    }

                    finalizerRedisDelete.addAll(toDelete);

                    if (finalizerRedisDelete.size() > props.getProducer().getPurgeThreshold()) {
                        // delete complected RPCs result from redis
                        try {
                            reactiveRedisTemplate
                                    .delete(Flux.fromIterable(toDelete))
                                    .subscribeOn(Schedulers.immediate()).publishOn(Schedulers.immediate())
                                    .block(deleteOperationsTimeout);
                        } catch (Exception e) {
                            log.error("Error delete redis keys {}", toDelete.size(), e);
                        } finally {
                            // we set redis ttl, so if we did not delete - it's ok
                            finalizerRedisDelete.clear();
                        }
                    }

                    // If thread which poll Redis finishes work (without errors) -
                    // healthcheck will set to out_of_service until the thread is found,
                    // new thread will be created
                    if (!isRedisPollingThreadHealthy()) {
                        startRedisPollingThread();
                    }

                    Thread.sleep(cleanerFinalizerInterval);
                } catch (Exception e) {
                    log.error("Error in finalizer RPC Redis Thread {}", namespace, e);
                }
            }
        });
//        cleanerThread.setPriority(Thread.MAX_PRIORITY);
        cleanerThread.setName("RPC-Finalizer-" + namespace);
        cleanerThread.start();
    }

    // thread for statistics handling
    private void startStatsThread() {
        final long statsInterval = props.getProducer().getStatsInterval();

        Thread redisPollingStats = new Thread(() -> {

            Map<RpcStatus, AtomicLong> prevStatsAll = new ConcurrentHashMap<>();
            initMetricMap(prevStatsAll);

            final boolean showCurrentState = props.getProducer().isShowCurrentStats();
            final boolean showAllState = props.getProducer().isShowAllStats();

            while (!Thread.currentThread().isInterrupted()) {
                StringBuilder s = new StringBuilder();

                long failedOverall = metricsOverall.get(RpcStatus.FAILED_TIMEOUT).get();
                long timeoutBetweenPollingDiff = failedOverall - prevFailedStats;
                inFailedByTimeoutState = timeoutBetweenPollingDiff > timeoutFailedThreshold;

                prevFailedStats = failedOverall;

                if (showCurrentState) {
                    metricsCurrent.forEach((m, n) -> {
                        s.append(m.name()).append(" = ").append(n.get()).append(", ");
                    });
                    if (metricsCurrent.size() > 0) {
                        log.info("Stats current {}", s.toString());
                        metricsCurrent.clear();
                    }
                }

                StringBuilder sAll = new StringBuilder();

                metricsOverall.forEach((m, n) -> {
                    if (isTerminateStatus(m)) {
                        sAll.append(m.name()).append(" = ").append(n.get()).append(", ");
                    }
                });

                if (metricsOverall.size() > 0) {

                    if (showAllState) {
                        long prevRpcSuccess = prevStatsAll.get(RpcStatus.DONE_OK).get();
                        long success = metricsOverall.get(RpcStatus.DONE_OK).get();
                        long successRpc = (success - prevRpcSuccess) / (statsInterval / 1000);

                        log.debug("Producer {}, Stats {} SUCCESS_RPC {}", namespace, sAll.toString(), successRpc);
                    }

                    metricsOverall.forEach((x, currentMetric) -> {
                        AtomicLong prevMetric = prevStatsAll.get(x);
                        if (prevMetric != null) {
                            meterRegistry.counter(mapRpcStatusToMetricName(x),
                                    "type", "RPC", "RPC", "Producer", "namespace", namespace).increment(currentMetric.get() - prevMetric.get());
                            prevMetric.set(currentMetric.get());
                        }
                    });
                }

                try {
                    Thread.sleep(statsInterval);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        redisPollingStats.setName("RPC-Redis-Polling-Stats-" + namespace);
        redisPollingStats.start();
    }

    private synchronized void startRedisPollingThread() {
        // double check
        if (redisThread != null && redisThread.isAlive()) {
            return;
        }

        redisThread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {

                    if (currentRpc.size() == 0) {
                        Thread.sleep(redisPollingIdleInterval);
                        continue;
                    }
                    ArrayList<String> keys = new ArrayList<>();

                    for (Map.Entry<String, RpcState> state : currentRpc.entrySet()) {
                        RpcStatus status = state.getValue().getStatus();
                        if (!RpcStatus.PROCESSING.equals(status) && !RpcStatus.TO_PROCESS_QUEUE.equals(status) && !isTerminateStatus(status)) {
                            keys.add(state.getKey());
                        }
                    }

                    if (keys.size() == 0) {
                        Thread.sleep(redisPollingInterval);
                        continue;
                    }

                    List<String> responseData = (List<String>) reactiveRedisTemplate.opsForValue()
                            .multiGet(keys)
                            .subscribeOn(Schedulers.immediate()).publishOn(Schedulers.immediate())
                            .block(pollingOperationsTimeout);

                    if (responseData == null) {
                        continue;
                    }


                    for (int i = 0; i < responseData.size(); i++) {
                        // responseData - sorted by sent keys
                        String response = responseData.get(i);

                        // null - if there is no key value in Redis
                        if (response != null) {
                            String key = keys.get(i);

                            RpcState state = currentRpc.get(key);
                            if (state != null) {
                                state.setStatus(RpcStatus.TO_PROCESS_QUEUE);
                                rpcResultExecutorPool.execute(() -> {
                                    processResponse(key, state, response);
                                });
                            } else {
                                log.warn("Lost RPC key={}. Is it cancelled by timeout??", key);
                            }
                        }
                    }

                    Thread.sleep(redisPollingInterval);
                }
            } catch (Exception e) {
                log.error("Error in polling loop", e);
            }
            // TODO:
            // ApplicationUtils.forceExitError();
        });
        redisThread.setName(RPC_REDIS_POLLING + namespace);
        redisThread.setUncaughtExceptionHandler((t, e) -> {
            log.error("Failed in thread {}", RPC_REDIS_POLLING + namespace, e);
            // TODO:
            // ApplicationUtils.forceExitError();
        });
        redisThread.setDaemon(true);
        redisThread.start();
    }

    @Override
    public <T extends RpcResultType, N extends RpcRequest> Mono<RpcResult<T>> callWithoutContext(N request, String topic, long timeout, Class<T> result) {
        return call(null, request, topic, timeout, result);
    }

    @Override
    public <T extends RpcResultType, N extends RpcRequest> Mono<RpcResult<T>> callWithoutContext(N request, String topic, Class<T> result) {
        return call(null, request, topic, result);
    }

    @Override
    public <T extends RpcResultType, N extends RpcRequest> Mono<RpcResult<T>> call(RpcCtx context, N request, String topic, Class<T> result) {
        return Mono.subscriberContext().flatMap(context1 -> {
            String opId = request.getClass().getSimpleName();

            // global operation timeout forwarding between MS call chains through RPC
            Optional<Object> orEmpty = context1.getOrEmpty(RpcContext.TIMEOUT);
            if (orEmpty.isPresent()) {

                OffsetDateTime now = OffsetDateTime.now();

                OffsetDateTime endExclusive = (OffsetDateTime) orEmpty.get();
                Duration operationTimeout = Duration.between(now, endExclusive);

                // if RPC operation timeout expired
                if (operationTimeout.isNegative()) {
                    return Mono.error(new StmExecutionError(String.format(
                            "RPC call %s of operation %s will not be called because timeout expired %s in MS %s",
                            opId,
                            context1.getOrDefault(RpcContext.OPERATION_ID, "")
                            , endExclusive, namespace)));

                }

                return call(context, request, topic, operationTimeout.toMillis(), result);
            }
            log.trace("Default timeout operation is used {} {}", opId, rpcTimeout);
            return call(context, request, topic, rpcTimeout, result);
        });
    }

    @Override
    public <T extends RpcResultType, N extends RpcRequest> Mono<RpcResult<T>> call(RpcCtx context, N request, String topic, Long timeout, Class<T> result) {
        return Mono.subscriberContext().flatMap(rpcCtx -> {

            String uid = UUID.randomUUID().toString();
            String traceId = rpcCtx.getOrDefault(RpcContext.TRACE_ID, uid);

            Duration rpcTimeout = Duration.of(timeout, ChronoUnit.MILLIS);

            return Mono.<RpcResult<T>>create(handler -> {
                OffsetDateTime finalTimeout = OffsetDateTime.now().plus(rpcTimeout);

                RpcState currentStatus = new RpcState(handler, result, RpcStatus.CREATED, finalTimeout);
                currentRpc.put(uid, currentStatus);

                HashMap<String, String> kafkaHeaders = new HashMap<>();
                kafkaHeaders.put(KAFKA_GLOBAL_OPERATION_ID_SENT, uid);

                Message<T> message = new GenericMessage(request, kafkaHeaders);

                // copied from KafkaTemplate
                ProducerRecord<?, ?> producerRecord = ((RecordMessageConverter) kafkaTemplate.getMessageConverter())
                        .fromMessage(message, topic);
                if (!producerRecord.headers().iterator().hasNext()) { // possibly no Jackson
                    byte[] correlationId = message.getHeaders().get(KafkaHeaders.CORRELATION_ID, byte[].class);
                    if (correlationId != null) {
                        producerRecord.headers().add(KafkaHeaders.CORRELATION_ID, correlationId);
                    }
                }

                producerRecord.headers().add(KAFKA_REDIS_RPC_TIMEOUT, finalTimeout.toString().getBytes());

                if (context != null) {
                    try {
                        producerRecord.headers().add(RpcContext.KAFKA_USER_CONTEXT, objectMapper.writeValueAsBytes(context));
                    } catch (JsonProcessingException e) {
                        log.error("Error write user context {}", context);
                        handler.error(e);
                        return;
                    }
                }

                producerRecord.headers().add(RpcContext.TRACE_ID, traceId.getBytes());

                kafkaTemplate.send(producerRecord).addCallback(new ListenableFutureCallback() {
                    @Override
                    public void onFailure(Throwable throwable) {
                        handler.error(throwable);
                        currentStatus.setStatus(RpcStatus.FAILED_TO_KAFKA);
                    }

                    @Override
                    public void onSuccess(Object o) {
                        currentStatus.setStatus(RpcStatus.SENT_TO_KAFKA);
                    }
                });

            }).timeout(rpcTimeout).onErrorMap(TimeoutException.class, e -> {
                String requestType = request.getClass().getSimpleName();

                log.error("Operation id={} type={} ns={} failed by timeout with {} ms traceId={}", uid, requestType, topic, timeout, traceId);

                RpcState rpcState = currentRpc.get(uid);
                if (rpcState != null) {
                    rpcState.setStatus(RpcStatus.FAILED_TIMEOUT);
                }

                return new RpcTimeoutException(uid, traceId, requestType, e.getMessage());
            });
        });
    }

    @Override
    public <T extends RpcResultType, N extends RpcRequest> Mono<RpcResult<T>> call(RpcCtx context, N request, String topic, String namespace, Long timeout, Class<T> result) {
        return call(context, request, topic, timeout, result);
    }

    @Override
    public <T extends RpcResultType, N extends RpcRequest> Mono<RpcResult<T>> call(RpcCtx context, N request, String topic, String namespace, Class<T> result) {
        return call(context, request, topic, result);
    }

    @Override
    public <T extends RpcResultType, N extends RpcRequest> Mono<RpcResult<T>> callWithoutContext(N request, String topic, String namespace, Class<T> result) {
        return call(null, request, topic, result);
    }

    @Override
    public <T extends RpcResultType, N extends RpcRequest> Mono<RpcResult<T>> callWithoutContext(N request, String topic, String namespace, long timeout, Class<T> result) {
        return call(null, request, topic, result);
    }

    @Override
    public String getName() {
        return "KAFKA_REDIS";
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    private void processResponse(String key, RpcState rpcState, String x) {
        rpcState.setStatus(RpcStatus.PROCESSING);

        Class resultClass = rpcState.getReturnClass();
        MonoSink handler = rpcState.getHandler();

        try {
            JavaType type = objectMapper.getTypeFactory().constructParametricType(RpcResult.class, resultClass);
            RpcResult resp = objectMapper.readValue(x, type);
            if (resp.isOk()) {
                handler.success(resp);
                rpcState.setStatus(RpcStatus.DONE_OK);
                log.trace("RPC propagate successful RPC result by key={} payload={}", key, resp);
            } else if (resp.getError() != null && RpcResult.InternalRpcResultErrorType.INTERNAL.equals(resp.getError().getType())) {
                handler.error(new RpcForwardingException(resp.getError().getMessage(), key));
                rpcState.setStatus(RpcStatus.DONE_FAILED);
                log.trace("RPC propagate INTERNAL ERROR RPC result by key={} payload={}", key, resp);
            } else if (resp.getError() != null && RpcResult.InternalRpcResultErrorType.BUSINESS.equals(resp.getError().getType())) {
                // in business error case - request completed successfully for this side
                handler.success(resp);
                rpcState.setStatus(RpcStatus.DONE_OK);
                log.trace("RPC propagate business ERROR RPC result by key={} payload={}", key, resp);
            }

        } catch (Exception e) {
            handler.error(e);
            log.error("Invalid response {} {}", key, x, e);
            rpcState.setStatus(RpcStatus.DONE_FAILED);
        }

    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        builder.up();

        if (!isRedisPollingThreadHealthy()) {
            builder.outOfService();
        }

        if (inFailedByTimeoutState) {
            builder.outOfService();
        }
    }

    private boolean isRedisPollingThreadHealthy() {
        if (redisThread != null && redisThread.isAlive()) {
            return true;
        }
        log.error(RPC_REDIS_POLLING + " thread is not found!");
        return false;
    }

}
