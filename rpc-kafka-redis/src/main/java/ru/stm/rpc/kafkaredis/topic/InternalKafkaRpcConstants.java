package ru.stm.rpc.kafkaredis.topic;

import org.springframework.kafka.support.KafkaHeaders;

public class InternalKafkaRpcConstants {

    // kafka as header
    public static final String KAFKA_GLOBAL_OPERATION_ID_SENT = KafkaHeaders.MESSAGE_KEY;
    public static final String KAFKA_GLOBAL_OPERATION_ID = KafkaHeaders.RECEIVED_MESSAGE_KEY;

}
