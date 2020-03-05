package ru.stm.rpc.kafkaredis.topic;

import lombok.Data;

@Data
public class KafkaTopicState {

    private KafkaTopicType type;
    private String topicName;

    /**
     * kafka topic for local transaction
     *
     * @param topicName
     * @return
     */
    public static KafkaTopicState transactional(String topicName) {
        KafkaTopicState st = new KafkaTopicState();
        st.setTopicName(topicName);
        st.setType(KafkaTopicType.TRANSACTIONAL_KAFKA);

        return st;
    }

    /**
     * Standard kafka topic
     *
     * @param topicName
     * @return
     */
    public static KafkaTopicState standard(String topicName) {
        KafkaTopicState st = new KafkaTopicState();
        st.setTopicName(topicName);
        st.setType(KafkaTopicType.SIMPLE_KAFKA);

        return st;
    }

    public enum KafkaTopicType {
        SIMPLE_KAFKA,
        TRANSACTIONAL_KAFKA,

    }

}
