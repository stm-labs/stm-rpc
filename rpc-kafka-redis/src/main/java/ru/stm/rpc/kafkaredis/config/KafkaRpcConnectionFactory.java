package ru.stm.rpc.kafkaredis.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import ru.stm.platform.StmExecutionError;
import ru.stm.rpc.kafkaredis.serialize.KafkaJsonDeserializer;
import ru.stm.rpc.kafkaredis.serialize.RpcSerializer;
import ru.stm.rpc.kafkaredis.serialize.KafkaSerializer;

import java.util.*;

@Slf4j
public class KafkaRpcConnectionFactory {

    @Deprecated(forRemoval = true)
    public static ConcurrentKafkaListenerContainerFactory createRpcListener(KafkaRedisRpcProperties.KafkaRedisRpcItem props) {
        return defaultListenerContainer(props);
    }

    public static ConcurrentKafkaListenerContainerFactory createRpcListener(KafkaRedisRpcProperties.KafkaRedisRpcItem props,
                                                                             ApplicationContext applicationContext) {
        return defaultListenerContainer(props, applicationContext);
    }

    public static KafkaTemplate createKafkaTemplate(String bootstrapServer) {
        return new KafkaTemplate<>(defaultProducerFactory(bootstrapServer));
    }

    public static KafkaTemplate createKafkaTemplate(String bootstrapServer, Integer maxRequestSize) {
        return new KafkaTemplate<>(defaultProducerFactory(bootstrapServer, maxRequestSize));
    }
    
    private static ProducerFactory defaultProducerFactory(String bootstrapServers) {
        if (StringUtils.isEmpty(bootstrapServers)) {
            throw new IllegalArgumentException("bootstrapServers is empty");
        }

        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaSerializer.class);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    private static ProducerFactory defaultProducerFactory(String bootstrapServers, Integer maxRequestSize) {
        if (StringUtils.isEmpty(bootstrapServers)) {
            throw new IllegalArgumentException("Пустой bootstrapServers");
        }

        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaSerializer.class);

        if (maxRequestSize != null && maxRequestSize != 0) {
            log.info("Producer property 'max.request.size' set to {} bytes", maxRequestSize);
            configProps.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, maxRequestSize);
        }
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Deprecated(forRemoval = true)
    private static ConcurrentKafkaListenerContainerFactory defaultListenerContainer(KafkaRedisRpcProperties.KafkaRedisRpcItem rpcProps) {
        ConcurrentKafkaListenerContainerFactory factory = new ConcurrentKafkaListenerContainerFactory<>();

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, rpcProps.getConsumer().getKafka().getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, rpcProps.getConsumer().getKafka().getGroupId());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaJsonDeserializer.class);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.getContainerProperties().setMissingTopicsFatal(false);

        KafkaJsonDeserializer jsonDeserializer = new KafkaJsonDeserializer();
        DefaultKafkaConsumerFactory consumerFactory = new DefaultKafkaConsumerFactory(props, new StringDeserializer(), jsonDeserializer);
        factory.setConsumerFactory(consumerFactory);
        return factory;
    }

    private static ConcurrentKafkaListenerContainerFactory defaultListenerContainer(KafkaRedisRpcProperties.KafkaRedisRpcItem rpcProps,
                                                                                     ApplicationContext applicationContext) {
        ConcurrentKafkaListenerContainerFactory factory = new ConcurrentKafkaListenerContainerFactory<>();

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, rpcProps.getConsumer().getKafka().getBootstrapServers());

        var appName = rpcProps.getConsumer().getKafka().getGroupId();

        // every application (microservice) has it's own consumer group
        if (rpcProps.getConsumer().getKafka().isEnableUniqConsumerGroupByApplicationId()) {

            Map<String, Object> annotatedBeans = applicationContext.getBeansWithAnnotation(SpringBootApplication.class);
            if (!annotatedBeans.isEmpty()) {
                var first = annotatedBeans.keySet().stream().findFirst();
                if (first.isPresent()) {
                    appName = appName + "__" + first.get();
                }
            }
        }
        log.trace("Use consumerGroupId={} for rpcProps={}", appName, rpcProps);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, appName);

        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaJsonDeserializer.class);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.getContainerProperties().setMissingTopicsFatal(false);

        KafkaJsonDeserializer jsonDeserializer = new KafkaJsonDeserializer();
        DefaultKafkaConsumerFactory consumerFactory = new DefaultKafkaConsumerFactory(props, new StringDeserializer(), jsonDeserializer);
        factory.setConsumerFactory(consumerFactory);
        return factory;
    }

    public static ReactiveRedisTemplate<String, Object> rpcRedis(KafkaRedisRpcProperties.KafkaRedisRpcItem props) {
        String nodes = props.getRedis().getNodes();

        if (StringUtils.isEmpty(nodes)) {
            log.warn("Standalone redis is used {}", props);

            LettuceConnectionFactory reactiveRedisConnectionFactory = new LettuceConnectionFactory(props.getRedis().getHost(), props.getRedis().getPort());
            reactiveRedisConnectionFactory.afterPropertiesSet();
            return reactiveRedisTemplate(reactiveRedisConnectionFactory);
        }

        if (KafkaRedisRpcProperties.REDIS_NODE_MODE_SENTINEL.equals(props.getRedis().getNodeMode())) {
            log.trace("Sentinel redis is used {}", props);

            RedisSentinelConfiguration redisSentinelConfiguration = new RedisSentinelConfiguration();

            String[] spitedNodes = nodes.split(",");
            HashSet<String> hashMap = new HashSet<>(Arrays.asList(spitedNodes));

            redisSentinelConfiguration.setMaster(props.getRedis().getClusterName());

            appendSentinels(hashMap, redisSentinelConfiguration);

            LettuceConnectionFactory reactiveRedisConnectionFactory = new LettuceConnectionFactory(redisSentinelConfiguration);
            reactiveRedisConnectionFactory.afterPropertiesSet();
            return reactiveRedisTemplate(reactiveRedisConnectionFactory);
        }
        throw new StmExecutionError("Unknown mode for Redis " + props);
    }

    private static void appendSentinels(Set<String> hostAndPorts, RedisSentinelConfiguration configuration) {
        Iterator var2 = hostAndPorts.iterator();

        while (var2.hasNext()) {
            String hostAndPort = (String) var2.next();
            configuration.addSentinel(readHostAndPortFromString(hostAndPort));
        }
    }

    private static RedisNode readHostAndPortFromString(String hostAndPort) {
        String[] args = StringUtils.split(hostAndPort, ":");
        Assert.notNull(args, "HostAndPort need to be seperated by  ':'.");
        Assert.isTrue(args.length == 2, "Host and Port String needs to specified as host:port");
        return new RedisNode(args[0], Integer.valueOf(args[1]));
    }

    private static ReactiveRedisTemplate<String, Object> reactiveRedisTemplate(ReactiveRedisConnectionFactory reactiveRedisConnectionFactory) {
        // use jackson as serializer to have readable keys and values. As a downside byte[] is encoded to base64 (because of json)
        return new ReactiveRedisTemplate(reactiveRedisConnectionFactory, RedisSerializationContext.fromSerializer(
                new GenericJackson2JsonRedisSerializer(RpcSerializer.getObjectMapper())));
    }

}
