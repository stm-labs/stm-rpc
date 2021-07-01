package ru.stm.rpc.kafkaredis;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.IfProfileValue;

import ru.stm.rpc.core.Rpc;
import ru.stm.rpc.kafkaredis.beanregistry.RpcTopicParser;
import ru.stm.rpc.kafkaredis.config.KafkaRedisRpcProperties;
import ru.stm.rpc.kafkaredis.topic.KafkaTopicState;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static ru.stm.rpc.kafkaredis.topic.KafkaEnsureTopicHelper.handleTopics;

@SpringBootTest(classes = {KafkaRpcTests.class, RpcTopicParser.class})
@Slf4j
@EnableConfigurationProperties(value = {KafkaRedisRpcProperties.class})
@IfProfileValue(name = "test-profile", values = {"IntegrationTest", "ciTest"})
@Configuration
public class KafkaRpcTests {

    private final static String NAMESPACE_DMZ = "dmz";
    private final static String NAMESPACE_INSIDE = "inside";
    private static final String KAFKA_TOPIC_NOTIFICATION_SENDER = "KAFKA_TOPIC_NOTIFICATION_SENDER";
    private static final String KAFKA_TOPIC_INSIDE_MS = "KAFKA_TOPIC_INSIDE_MS";
    private static final String KAFKA_TOPIC_GP3DOCS = "KAFKA_TOPIC_GP3DOCS";

    @Autowired
    private KafkaRedisRpcProperties rpcProps;

    private volatile boolean allPrepared;

    private AtomicReference<Throwable> throwableRef = new AtomicReference<>();

    private final Thread.UncaughtExceptionHandler exceptionHandler = (th, ex) -> {
        throwableRef.set(ex);
        log.error("Uncaught exception", ex);
    };

    @Autowired
    RpcTopicParser rpcTopicParser;

    @Test
    public void testNotSpellTopic() {
        String parse = rpcTopicParser.parse(TestNotSpellAnnotation.class.getAnnotation(Rpc.class));
        Assertions.assertEquals("test", parse);
    }

    @Test
    public void testNotSpellTopic2() {
        String parse = rpcTopicParser.parse(TestNotSpellAnnotation2.class.getAnnotation(Rpc.class));
        Assertions.assertEquals("#{systemEnvironment['HOME']}", parse);
    }

    @Test
    public void TestSpellAnnotation() {
        String parse = rpcTopicParser.parse(TestSpellAnnotation.class.getAnnotation(Rpc.class));
        Assertions.assertEquals("param", parse);
    }
    
    // verify that we can handle concurrent topic creating properly
    @Test
    public void checkConcurrentTopicCreation() throws InterruptedException {

        log.info("Got={}", rpcProps);
        
        Runnable runnable = () -> {

            Map<String, Object> configs = new HashMap<>();
            configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                    rpcProps.getNamespace().get(NAMESPACE_DMZ).getConsumer().getKafka().getBootstrapServers());

            AdminClient dmzAdmin = AdminClient.create(configs);

            Map<String, Object> configsInside = new HashMap<>();
            configsInside.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                    rpcProps.getNamespace().get(NAMESPACE_INSIDE).getConsumer().getKafka().getBootstrapServers());

            AdminClient insideAdmin = AdminClient.create(configsInside);

            Map<String, Collection<KafkaTopicState>> topics = new HashMap<>();

            topics.put(NAMESPACE_INSIDE, Arrays.asList(sender(), insideMs()));
            topics.put(NAMESPACE_DMZ, Arrays.asList(gp3()));

            dmzAdmin.deleteTopics(topics.get(NAMESPACE_DMZ).stream().map(x -> x.getTopicName()).collect(Collectors.toList()));
            insideAdmin.deleteTopics(topics.get(NAMESPACE_INSIDE).stream().map(x -> x.getTopicName()).collect(Collectors.toList()));

            while (!allPrepared) {
                // burn CPU so we continue execution immediately after flag change: for better concurrency
            }

            handleTopics(rpcProps, topics);
        };

        List<Thread> threads = new ArrayList<>();

        threads.add(new Thread(runnable));
        threads.add(new Thread(runnable));
        threads.add(new Thread(runnable));
        threads.add(new Thread(runnable));
        threads.add(new Thread(runnable));

        for (Thread thread : threads) {
            thread.setUncaughtExceptionHandler(exceptionHandler);
            thread.start();
        }

        // give some time to start threads and stay on while(!allPrepared)
        Thread.sleep(2000);

        allPrepared = true;

        // wait all threads to stop
        for (Thread thread : threads) {
            thread.join();
        }

        if (throwableRef.get() != null) {
            throw new RuntimeException(throwableRef.get());
        }
    }

    private KafkaTopicState sender() {
        return KafkaTopicState.standard(KAFKA_TOPIC_NOTIFICATION_SENDER);
    }

    private KafkaTopicState insideMs() {
        return KafkaTopicState.standard(KAFKA_TOPIC_INSIDE_MS);
    }

    private KafkaTopicState gp3() {
        return KafkaTopicState.standard(KAFKA_TOPIC_GP3DOCS);
    }

}
