package ru.stm.rpc.kafkaredis.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.stm.platform.BusinessError;
import ru.stm.platform.StmExecutionError;
import ru.stm.rpc.core.RpcResult;
import ru.stm.rpc.kafkaredis.config.KafkaRpcConnection;
import ru.stm.rpc.kafkaredis.serialize.RpcSerializer;
import ru.stm.rpc.types.RpcResultType;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class RpcResponseService {

    private static final Logger logger = LoggerFactory.getLogger(RpcResponseService.class);
    private final ObjectMapper objectMapper = RpcSerializer.getObjectMapper();

    private final AtomicLong sentResponsesOk = new AtomicLong(0);
    private final AtomicLong sentResponsesFailed = new AtomicLong(0);
    private final AtomicLong sendException = new AtomicLong(0);

    private final Map<String, KafkaRpcConnection> kafkaRpcConnection = new HashMap<>();

    private final MeterRegistry meterRegistry;

    public RpcResponseService(List<KafkaRpcConnection> kafkaRpcConnections, MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        kafkaRpcConnections.forEach(x -> {
            kafkaRpcConnection.put(x.getNamespace(), x);
        });
    }

    /**
     * Send a unsuccessful operation result with internal error type
     *
     * @param key       global operation ID
     * @param throwable
     */
    void sendExceptionally(Object payload, String key, String traceId, String operationName, Throwable throwable, String namespace, Duration timeout) {
        logger.error("Send error to operationId={} traceId={} operationName={} payload={}", key, traceId, operationName, payload, throwable);

        RpcResult rpcResult = RpcResult.errorInternal(key, throwable);

        try {
            KafkaRpcConnection connection = this.kafkaRpcConnection.get(namespace);

            // do not use retryBackoff to prevent unnecessary errors like timeout errors or RpcExpiredException
            connection.getRedisTemplate().opsForValue().set(key, getRpcRedisValue(rpcResult, key, traceId, operationName, connection), connection.getRedisttl())
                    .timeout(timeout)
                    .doOnError(x -> {
                        logger.error("Error send response to redis key={} operation={}", key, operationName, x);
                        this.meterRegistry.counter("rpc_send_exceptionally", "RPC", "Consumer",
                                "outcome", "error", "namespace", namespace, "operation_name", operationName, "component", "redis").increment();

                        sentResponsesFailed.incrementAndGet();
                    })
                    .subscribe(x -> {
                        this.sendException.incrementAndGet();
                        this.meterRegistry.counter("rpc_send_exceptionally", "RPC", "Consumer",
                                "outcome", "success", "namespace", namespace, "operation_name", operationName, "component", "response").increment();
                    });
        } catch (JsonProcessingException e) {
            this.meterRegistry.counter("rpc_send_exceptionally", "RPC", "Consumer",
                    "outcome", "error", "namespace", namespace, "operation_name", operationName, "component", "json").increment();

            logger.error("failed to send error response to redis {}", key, e);
        }
    }

    /**
     * Send a successful operation result
     *
     * @param key global operation ID
     * @param v   main payload
     */
    <T extends RpcResultType> void sendSuccess(Object payload, String key, String traceId, String operationName, T v, String namespace, Duration timeout) {
        try {
            RpcResult<T> rpcResult = RpcResult.success(key, v);
            KafkaRpcConnection connection = this.kafkaRpcConnection.get(namespace);

            connection.getRedisTemplate().opsForValue().set(key, getRpcRedisValue(rpcResult, key, traceId, operationName, connection), connection.getRedisttl())
                    .retryBackoff(connection.getProps().getConsumer().getRetryTimes(), connection.getConsumerBackoff())
                    .timeout(timeout)
                    .doOnError(x -> {
                        this.meterRegistry.counter("rpc_send_success", "RPC", "Consumer",
                                "outcome", "error", "namespace", namespace, "operation_name", operationName, "component", "redis").increment();
                        logger.error("Error send response to redis key={} operation={}", key, operationName, x);
                        sentResponsesFailed.incrementAndGet();
                    })
                    .subscribe(x -> {
                        if (x instanceof Boolean && (Boolean) x) {
                            this.meterRegistry.counter("rpc_send_success", "RPC", "Consumer",
                                    "outcome", "success", "namespace", namespace, "operation_name", operationName, "component", "response").increment();
                            sentResponsesOk.incrementAndGet();
                            logger.trace("sent to redis key={} operation={}", key, operationName);
                        } else {
                            this.meterRegistry.counter("rpc_send_success", "RPC", "Consumer",
                                    "outcome", "error", "namespace", namespace, "operation_name", operationName, "component", "redis").increment();
                            throw new RuntimeException("Can not send to redis key " + key);
                        }
                    });
        } catch (Exception e) {
            sendExceptionally(payload, key, traceId, operationName, e, namespace, timeout);
            logger.error("Error serialize to redis request {}", key, e);
        }
    }

    /**
     * Send a successful operation result
     *
     * @param key global operation ID
     */
    void sendErrorBusiness(Object payload, String key, String traceId, String operationName, BusinessError businessError, String namespace, Duration timeout) {
        try {
            RpcResult rpcResult = RpcResult.errorBusinessString(key, businessError.getCode(), businessError.getMessage(), businessError.getArgs());
            KafkaRpcConnection connection = this.kafkaRpcConnection.get(namespace);

            connection.getRedisTemplate().opsForValue().set(key, getRpcRedisValue(rpcResult, key, traceId, operationName, connection), connection.getRedisttl())
                    .retryBackoff(connection.getProps().getConsumer().getRetryTimes(), connection.getConsumerBackoff())
                    .timeout(timeout)
                    .doOnError(x -> {
                        this.meterRegistry.counter("rpc_send_error_business", "RPC", "Consumer",
                                "outcome", "error", "namespace", namespace, "operation_name", operationName, "component", "redis").increment();

                        logger.error("Error send response to redis key={} operation={}", key, operationName, x);
                        sentResponsesFailed.incrementAndGet();
                    })
                    .subscribe(x -> {
                        this.meterRegistry.counter("rpc_send_error_business", "RPC", "Consumer",
                                "outcome", "success", "namespace", namespace, "operation_name", operationName, "component", "response").increment();

                        sentResponsesOk.incrementAndGet();
                        logger.trace("sent to redis {} code {}", key, x);
                    });
        } catch (JsonProcessingException e) {
            logger.error("Error serialize to redis request key={}", key, e);
            sendExceptionally(payload, key, traceId, operationName, e, namespace, timeout);
        }
    }

    private String getRpcRedisValue(RpcResult rpcResult, String key, String traceId, String operationName, KafkaRpcConnection conn) throws JsonProcessingException {
        String s = objectMapper.writeValueAsString(rpcResult);

        if (s.length() > conn.getProps().getResponseWarnThreshold()) {
            int printLength = conn.getProps().getPrintLength();
            if (s.length() < printLength) {
                printLength = s.length() - 1;
            }

            if (s.length() > conn.getProps().getResponseRefuseThreshold()) {

                logger.error("Too much value in Redis: key={} traceId={} operationName={} length={} response={}",
                        key, traceId, operationName, s.length(), s.subSequence(0, printLength));

                // do not allow recording if RPC response is too large
                throw new StmExecutionError(String.format("RPC Response is too large key=%s traceId=%s operationName==%s result length=%s",
                        key, traceId, operationName, s.length()));
            }
            logger.warn("RPC large enough value in Redis: key={} traceId={} operationName={} response={}", key, traceId, operationName,
                    s.subSequence(0, printLength));
        }
        return s;
    }

    /**
     * @return amount of successful responses sent to RPC, included business error
     */
    AtomicLong getSentResponsesOk() {
        return sentResponsesOk;
    }

    /**
     * @return an error occurred while sent the response to RPC
     */
    AtomicLong getSentResponsesFailed() {
        return sentResponsesFailed;
    }

    /**
     * @return response was successfully sent to RPC that a technical error occurred while processing the current request
     */
    AtomicLong getSentException() {
        return sendException;
    }
}
