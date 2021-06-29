package ru.stm.rpc.kafkaredis.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.util.StringUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.AbstractMessageListenerContainer;
import org.springframework.kafka.listener.AcknowledgingConsumerAwareMessageListener;
import org.springframework.kafka.support.Acknowledgment;
import ru.stm.rpc.kafkaredis.config.KafkaRedisRpcProperties.KafkaConsumerConfiguration;
import ru.stm.rpc.kafkaredis.config.KafkaRedisRpcProperties.KafkaProducerConfiguration;
import ru.stm.rpc.kafkaredis.config.KafkaRedisRpcProperties.KafkaRedisRpcItem;
import ru.stm.rpc.kafkaredis.config.KafkaRpcConnection;
import ru.stm.rpc.kafkaredis.consumer.RpcAbstractRpcListener;
import ru.stm.rpc.kafkaredis.consumer.RpcResponseService;
import ru.stm.rpc.kafkaredis.producer.KafkaRedisRPCProducer;
import ru.stm.rpc.kafkaredis.util.RpcDirection;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static ru.stm.rpc.kafkaredis.config.KafkaRpcConnectionFactory.*;

@Getter
@Slf4j
public class RpcNamespace {

    private static final int REDIS_DEFAULT_PORT = 6379;

    private final String name;
    private final KafkaRedisRpcItem props;
    private final ApplicationContext ctx;
    private final MeterRegistry meterRegistry;

    private final Map<String, RpcTopic> topics;
    private final Set<String> consumerTopics;

    private ConcurrentKafkaListenerContainerFactory kafkaConsumer;
    private KafkaTemplate kafkaProducer;
    private ReactiveRedisTemplate<String, Object> redis;
    private KafkaRpcConnection rpcConn;
    private KafkaRedisRPCProducer rpcProducer;
    private RpcAbstractRpcListener rpcListener;

    public RpcNamespace(String name, KafkaRedisRpcItem props, ApplicationContext ctx, MeterRegistry meterRegistry) {
        this.name = name;
        this.props = props;
        this.ctx = ctx;
        this.meterRegistry = meterRegistry;

        this.topics = new HashMap<>();
        this.consumerTopics = new HashSet<>();

        log.info("Found RPC Namespace '{}', hasConsumer={}, hasProducer={}, hasRedis={}",
                name, hasConsumer(), hasProducer(), hasRedis());

        verify();
        configure();
    }

    /**
     * 1. Verify RPC property configuration
     */
    private void verify() {
        if (hasConsumer()) {
            KafkaConsumerConfiguration kafka = props.getConsumer().getKafka();
            if (kafka == null || StringUtils.isBlank(kafka.getBootstrapServers())) {
                throw new IllegalArgumentException(String.format("Consumer for namespace %s was set but Kafka Bootstrap Servers has not been setup", name));
            }

            log.info("RPC Namespace '{}': consumer servers: {}", name, kafka.getBootstrapServers());
        }
        if (hasProducer()) {
            KafkaProducerConfiguration kafka = props.getProducer().getKafka();
            if (kafka == null || StringUtils.isBlank(kafka.getBootstrapServers())) {
                throw new IllegalArgumentException(String.format("Producer for namespace %s was set but Kafka Bootstrap Servers has not been setup", name));
            }

            log.info("RPC Namespace '{}': producer servers: {}", name, kafka.getBootstrapServers());
        }
    }

    /**
     * 2. Create and configure RPC components
     */
    private void configure() {
        if (hasConsumer()) {
            kafkaConsumer = createRpcListener(props);
        }
        if (hasProducer()) {
            kafkaProducer = createKafkaTemplate(props.getProducer().getKafka().getBootstrapServers());
        }
        if (hasRedis()) {
            if (props.getRedis().getPort() == 0) {
                props.getRedis().setPort(REDIS_DEFAULT_PORT);

                log.info("RPC Namespace '{}': switching Redis port to default: {}", name, REDIS_DEFAULT_PORT);
            }

            redis = rpcRedis(props);
            rpcConn = createRpcConn();
        }
        if (hasRpcProducer()) {
            rpcProducer = new KafkaRedisRPCProducer(rpcConn, meterRegistry);
        }
    }

    private KafkaRpcConnection createRpcConn() {
        KafkaRpcConnection conn = new KafkaRpcConnection();
        conn.setNamespace(name);
        conn.setRedisTemplate(redis);
        conn.setProps(props);

        if (hasConsumer()) {
            conn.setRedisttl(Duration.of(props.getConsumer().getRedisTtl(), ChronoUnit.MILLIS));
            conn.setConsumerBackoff(Duration.of(props.getConsumer().getRetryBackoff(), ChronoUnit.MILLIS));
        }

        conn.setKafkaTemplate(kafkaProducer);
        return conn;
    }

    public Collection<RpcTopic> topics() {
        return topics.values();
    }

    public boolean hasConsumer() {
        return props.getConsumer() != null;
    }

    public boolean hasProducer() {
        return props.getProducer() != null;
    }

    public boolean hasRedis() {
        return props.getRedis() != null;
    }

    public boolean hasRpcProducer() {
        return hasRedis() && hasProducer();
    }

    public String getProducerServers() {
        if (!hasProducer()) {
            throw new IllegalArgumentException("No producer configuration for " + name);
        }
        return props.getProducer().getKafka().getBootstrapServers();
    }

    public String getConsumerServers() {
        if (!hasConsumer()) {
            throw new IllegalArgumentException("No consumer configuration for " + name);
        }
        return props.getConsumer().getKafka().getBootstrapServers();
    }

    /**
     * 3. Create topic for this namespace (if does not exist yet).
     *
     * @param direction Producer or consumer topic
     * @param topicName Topic name
     * @param transactional True if topic is transactional
     * @return Topic
     */
    public void createTopic(RpcDirection direction, String topicName, boolean transactional) {
        /* Create topic on first occurence */
        topics.computeIfAbsent(topicName, x -> new RpcTopic(name, topicName, transactional));

        /* If this is consumer topic, add to consumer topics list */
        if (direction == RpcDirection.CONSUMER) {
            consumerTopics.add(topicName);
        }
    }

    /**
     * 4. Add RPC beans with RpcHandler methods to corresponding topic.
     *
     * @param topicName Topic name
     * @param bean RPC bean
     */
    public void addListenerBean(String topicName, Object bean) {
        ensureTopic(topicName).addListenerBean(bean);
    }

    /**
     * 5. Creation of Kafka listener for all topics in this namespace
     *
     * @param rpcResponseService Response service for sending results
     */
    public void createListener(RpcResponseService rpcResponseService, ApplicationEvent event) {
        if (hasConsumer() && !consumerTopics.isEmpty() && rpcListener == null) {
            rpcListener = new RpcAbstractRpcListener(rpcResponseService, rpcConn, meterRegistry);

            log.info("Creating Kafka consumer container, namespace={}, topics={}", name, consumerTopics);

            /* Create and start kafka container */
            AbstractMessageListenerContainer cont = kafkaConsumer.createContainer(consumerTopics.toArray(new String[0]));
            cont.setupMessageListener(new Listener());
            cont.start();
        }
        if (rpcListener != null) {
            rpcListener.onApplicationEvent(event);
        }
    }

    /**
     * Kafka listener class
     */
    private class Listener implements AcknowledgingConsumerAwareMessageListener<String, String> {
        @Override
        public void onMessage(ConsumerRecord<String, String> data, Acknowledgment acknowledgment, Consumer<?, ?> consumer) {
            try {
                /* Check that topic handler exists */
                RpcTopic topic = topics.get(data.topic());
                if (topic == null) {
                    throw new UnsupportedOperationException("Unsupported topic " + data.topic() + ", req=" + data.value());
                }

                /* Call method handler for this topic */
                topic.handle(data, rpcListener, acknowledgment);

            } catch (Exception e) {
                log.error("Error processing RPC", e);
                acknowledgment.acknowledge();
            }
        }
    }

    private RpcTopic ensureTopic(String name) {
        RpcTopic topic = topics.get(name);
        if (topic == null) {
            throw new IllegalArgumentException("Incorrect topic name: " + name);
        }
        return topic;
    }
}
