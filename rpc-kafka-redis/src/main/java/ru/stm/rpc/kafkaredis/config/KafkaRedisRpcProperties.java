package ru.stm.rpc.kafkaredis.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.validation.constraints.NotEmpty;
import java.util.Map;

/**
 * Configuration for KafkaRedis RPC
 */
@Data
@ConfigurationProperties("stm.rpc.kafkaredis")
@Component
public class KafkaRedisRpcProperties {

    public static final String REDIS_NODE_MODE_SENTINEL = "Sentinel";
    private final static int DEFAULT_RPC_TIMEOUT = 30000;

    private Map<String, KafkaRedisRpcItem> namespace;

    @Data
    public static class KafkaRedisRpcItem {

        private long responseWarnThreshold = 200_000;

        // with 40_000_000 chars ~ 80 MB + JSON encoded payload
        private long responseRefuseThreshold = 40_000_000;
        private int printLength = 3000;

        private RedisConfiguration redis;
        private KafkaRedisProducer producer;
        private KafkaRedisConsumer consumer;

        private int loggingThreshold = 50000;
    }

    /**
     * Configuration Redis for Consumer & Producer
     */
    @Data
    public static class RedisConfiguration {

        // For Standalone mode
        private String host;
        private int port;

        // For Redis Sentinel (HA) mode
        private String nodes;
        private String nodeMode = REDIS_NODE_MODE_SENTINEL;
        private String clusterName = "mycluster";

    }

    /**
     * Configuration Kafka for Producer
     */
    @Data
    public static class KafkaProducerConfiguration {
        @NotEmpty
        private String bootstrapServers;
    }

    /**
     * Configuration Kafka for Consumer
     */
    @Data
    public static class KafkaConsumerConfiguration {

        @NotEmpty
        private String bootstrapServers;

        private String groupId = "RPC";
    }

    /**
     * Configuration for RPC Producer
     */
    @Data
    public static class KafkaRedisProducer {
        @NotEmpty
        private long timeout = DEFAULT_RPC_TIMEOUT;

        // two minutes timeout for system operation such as delete GC or poll batch
        @NotEmpty
        private long internalDeleteOperationTimeout = 30;

        // polling interval if we have not previous poll
        private long pollingOperationTimeout = 5;

        @NotEmpty
        private long redisPolling = 15;

        private long redisIdlePolling = redisPolling * 2;

        @NotEmpty
        private long statsInterval = 1000;
        @NotEmpty
        private long cleanerFinalizerInterval = 10;
        @NotEmpty
        private boolean showCurrentStats = false;
        @NotEmpty
        private boolean showAllStats = false;
        private KafkaProducerConfiguration kafka;

        private long finalizerStaleSec = 40;

        /**
         * threadshold errors in statsInterval
         */
        private int timeoutFailedThreshold = 5;

        private int purgeThreshold = 500;

    }


    /**
     * Configuration for RPC Consumer
     */
    @Data
    public static class KafkaRedisConsumer {
        private long statsInterval = 2000;
        private int executionTimeout = DEFAULT_RPC_TIMEOUT;
        private boolean showCurrentStats = false;
        private long redisTtl = (long) (DEFAULT_RPC_TIMEOUT * 1.5);
        private KafkaConsumerConfiguration kafka;

        private long retryTimes = 3;

        private long retryBackoff = 3000;

    }

}
