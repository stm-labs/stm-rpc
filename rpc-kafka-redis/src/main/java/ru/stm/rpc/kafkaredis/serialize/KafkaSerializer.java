package ru.stm.rpc.kafkaredis.serialize;

import org.springframework.kafka.support.serializer.JsonSerializer;

public class KafkaSerializer extends JsonSerializer {

    public KafkaSerializer() {
        super(RpcSerializer.getObjectMapper());
    }
}
