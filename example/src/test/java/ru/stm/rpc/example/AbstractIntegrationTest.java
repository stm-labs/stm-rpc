package ru.stm.rpc.example;

import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.lifecycle.Startables;

import java.util.stream.Stream;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = TestApplication.class)
@ContextConfiguration(
        initializers = AbstractIntegrationTest.Initializer.class
)
public abstract class AbstractIntegrationTest {

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        static KafkaContainer kafka = new KafkaContainer();

        static GenericContainer redis = new GenericContainer("redis:3-alpine").withExposedPorts(6379);

        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {

            Startables.deepStart(Stream.of(kafka, redis)).join();

            TestPropertyValues.of(
                    "stm.rpc.kafkaredis.namespace.test.producer.kafka.bootstrap-servers=" + kafka.getBootstrapServers(),
                    "stm.rpc.kafkaredis.namespace.test.consumer.kafka.bootstrap-servers=" + kafka.getBootstrapServers(),
                    "stm.rpc.kafkaredis.namespace.test.redis.host=" + redis.getContainerIpAddress(),
                    "stm.rpc.kafkaredis.namespace.test.redis.port=" + redis.getMappedPort(6379)
            ).applyTo(configurableApplicationContext);
        }
    }
}
