package ru.stm.rpc.kafkaredis.config;

import lombok.Data;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import java.time.Duration;

@Data
public class KafkaRpcConnection {

    // TODO: consumer (so far KafkaRpcConnection is only for Producer)
    private ReactiveRedisTemplate<String, Object> redisTemplate;
    private KafkaTemplate kafkaTemplate;
    private KafkaRedisRpcProperties.KafkaRedisRpcItem props;
    private String namespace;

    // TODO: generated
    private Duration redisttl;

    private Duration consumerBackoff;

}
