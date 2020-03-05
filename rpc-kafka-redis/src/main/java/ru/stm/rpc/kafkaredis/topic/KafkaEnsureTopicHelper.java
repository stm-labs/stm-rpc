package ru.stm.rpc.kafkaredis.topic;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.errors.UnsupportedVersionException;
import org.springframework.kafka.KafkaException;
import org.springframework.util.StringUtils;
import ru.stm.platform.StmExecutionError;
import ru.stm.rpc.kafkaredis.config.KafkaRedisRpcProperties;
import ru.stm.rpc.kafkaredis.service.RpcNamespace;
import ru.stm.rpc.kafkaredis.service.RpcTopic;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Kafka topics creating
 */
@Slf4j
public class KafkaEnsureTopicHelper {

    private static final int KAFKA_NUM_PARTITIONS = 12;
    private static final int DEFAULT_OPERATION_TIMEOUT = 30;
    private static int operationTimeout = DEFAULT_OPERATION_TIMEOUT;

    public static void handleTopics(KafkaRedisRpcProperties rpcProps, Map<String, Collection<KafkaTopicState>> topics) {

        rpcProps.getNamespace().forEach((namespace, value) -> {
            if (value.getProducer() != null && value.getProducer().getKafka() != null && !StringUtils.isEmpty(value.getProducer().getKafka().getBootstrapServers())) {
                createTopicsIfNeeded(namespace, topics.get(namespace), value.getProducer().getKafka().getBootstrapServers());
            }

            if (value.getConsumer() != null && value.getConsumer().getKafka() != null && !StringUtils.isEmpty(value.getConsumer().getKafka().getBootstrapServers())) {
                createTopicsIfNeeded(namespace, topics.get(namespace), value.getConsumer().getKafka().getBootstrapServers());
            }

        });
    }

    public static void handleTopics(KafkaRedisRpcProperties rpcProps, String namespace, Collection<KafkaTopicState> topics) {
        handleTopics(rpcProps, Collections.singletonMap(namespace, new ArrayList<>(topics)));
    }

    public static void handleTopics(Collection<RpcNamespace> namespaces) {
        namespaces.forEach(ns -> {
            if (ns.hasProducer()) {
                createTopicsIfNeeded(ns.getName(), createTopicStates(ns.topics()), ns.getProducerServers());
            }
            if (ns.hasConsumer()) {
                /* Optimization: do not attempt to create topics if bootstrap servers are the same as for producer */
                if (!ns.hasProducer() || !ns.getProducerServers().equals(ns.getConsumerServers())) {
                    createTopicsIfNeeded(ns.getName(), createTopicStates(ns.topics()), ns.getConsumerServers());
                }
            }
        });
    }

    private static Collection<KafkaTopicState> createTopicStates(Collection<RpcTopic> topics) {
        return topics.stream()
                .map(t -> t.isTransactional()
                        ? KafkaTopicState.transactional(t.getTopic())
                        : KafkaTopicState.standard(t.getTopic()))
                .collect(Collectors.toList());
    }

    private static void createTopicsIfNeeded(String namespace, Collection<KafkaTopicState> topics, String bootstrapServers) {
        if (topics == null) {
            return;
        }

        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        List<NewTopic> newTopics = topics.stream().map(x -> {

            // если у нас 3 bootstrap servers, то считаем что у нас кластерная конфигурация (что не факт конечно)
            boolean distributedKafka = bootstrapServers.split(",").length >= 2;
            short kafkaReplicas = (short) (distributedKafka ? 3 : 1);

            if (x.getType().equals(KafkaTopicState.KafkaTopicType.TRANSACTIONAL_KAFKA)) {
                return new NewTopic(x.getTopicName(), KAFKA_NUM_PARTITIONS, kafkaReplicas);
            }

            if (x.getType().equals(KafkaTopicState.KafkaTopicType.SIMPLE_KAFKA)) {
                return new NewTopic(x.getTopicName(), KAFKA_NUM_PARTITIONS, kafkaReplicas);
            }

            log.error("Invalid Kafka topic config {}", x);
            throw new StmExecutionError("Invalid Kafka Topic");
        }).collect(Collectors.toList());


        log.debug("RPC kafka topics NS={}, topics={}, bootstrap={}", namespace, topics, bootstrapServers);

        AdminClient client = AdminClient.create(configs);

        addTopicsIfNeeded(client, newTopics, new MutableInt(0));
        client.close();
    }

    // copied from org.springframework.kafka.core.KafkaAdmin with necessary changes
    private static void addTopicsIfNeeded(AdminClient adminClient, Collection<NewTopic> topics, MutableInt retry) {
        if (topics.size() > 0) {
            Map<String, NewTopic> topicNameToTopic = new HashMap<>();
            topics.forEach(t -> topicNameToTopic.compute(t.name(), (k, v) -> v = t));
            DescribeTopicsResult topicInfo = adminClient
                    .describeTopics(topics.stream()
                            .map(NewTopic::name)
                            .collect(Collectors.toList()));
            List<NewTopic> topicsToAdd = new ArrayList<>();
            Map<String, NewPartitions> topicsToModify = new HashMap<>();
            topicInfo.values().forEach((n, f) -> {
                NewTopic topic = topicNameToTopic.get(n);
                try {
                    TopicDescription topicDescription = f.get(operationTimeout, TimeUnit.SECONDS);
                    if (topic.numPartitions() < topicDescription.partitions().size()) {
                        if (log.isInfoEnabled()) {
                            log.info(String.format(
                                    "Topic '%s' exists but has a different partition count: %d not %d", n,
                                    topicDescription.partitions().size(), topic.numPartitions()));
                        }
                    } else if (topic.numPartitions() > topicDescription.partitions().size()) {
                        if (log.isInfoEnabled()) {
                            log.info(String.format(
                                    "Topic '%s' exists but has a different partition count: %d not %d, increasing "
                                            + "if the broker supports it", n,
                                    topicDescription.partitions().size(), topic.numPartitions()));
                        }
                        topicsToModify.put(n, NewPartitions.increaseTo(topic.numPartitions()));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (TimeoutException e) {
                    throw new KafkaException("Timed out waiting to get existing topics", e);
                } catch (ExecutionException e) {
                    topicsToAdd.add(topic);
                }
            });
            if (topicsToAdd.size() > 0) {
                CreateTopicsResult topicResults = adminClient.createTopics(topicsToAdd);
                try {
                    topicResults.all().get(operationTimeout, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Interrupted while waiting for topic creation results", e);
                } catch (TimeoutException e) {
                    throw new KafkaException("Timed out waiting for create topics results", e);
                } catch (ExecutionException e) {
                    log.error("Failed to create topics", e.getCause());
                    doRetryAlreadyExists(adminClient, topics, retry);
                    return;
                }
            }
            if (topicsToModify.size() > 0) {
                CreatePartitionsResult partitionsResult = adminClient.createPartitions(topicsToModify);
                try {
                    partitionsResult.all().get(operationTimeout, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Interrupted while waiting for partition creation results", e);
                } catch (TimeoutException e) {
                    throw new KafkaException("Timed out waiting for create partitions results", e);
                } catch (ExecutionException e) {
                    log.error("Failed to create partitions", e.getCause());
                    if (!(e.getCause() instanceof UnsupportedVersionException)) {
                        throw new KafkaException("Failed to create partitions", e.getCause());
                    }
                }
            }
        }
    }

    private static void doRetryAlreadyExists(AdminClient adminClient, Collection<NewTopic> topics, MutableInt retry) {
        log.warn("Topics already created. Will run operation one more time");
        retry.increment();
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        addTopicsIfNeeded(adminClient, topics, retry);
    }

}
