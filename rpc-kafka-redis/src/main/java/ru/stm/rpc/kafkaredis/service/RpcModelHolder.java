package ru.stm.rpc.kafkaredis.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.kafka.core.KafkaTemplate;
import ru.stm.rpc.kafkaredis.config.KafkaRedisRpcProperties;
import ru.stm.rpc.kafkaredis.config.KafkaRedisRpcProperties.KafkaRedisRpcItem;
import ru.stm.rpc.kafkaredis.config.KafkaRpcConnection;
import ru.stm.rpc.kafkaredis.consumer.RpcResponseService;
import ru.stm.rpc.kafkaredis.util.RpcDirection;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RpcModelHolder {

    private final ConfigurableBeanFactory factory;
    private final ApplicationContext ctx;
    private final MeterRegistry meterRegistry;

    private final Map<String, RpcNamespace> namespaces = new HashMap<>();

    private boolean hasAnyConsumer;
    private RpcResponseService rpcResponseService;

    public RpcModelHolder(ConfigurableBeanFactory factory, ApplicationContext ctx, KafkaRedisRpcProperties props) {
        this.factory = factory;
        this.ctx = ctx;
        this.meterRegistry = factory.getBean(MeterRegistry.class);

        createModel(props);
    }

    /**
     * 1. Parse RPC configuration properties and build model tree.
     *
     * @param props Properties
     */
    private void createModel(KafkaRedisRpcProperties props) {
        /* Create all components for each namespace */
        props.getNamespace().forEach(this::createNamespace);

        /* Create RpcResponseService if any consumer exists */
        if (hasAnyConsumer) {
            rpcResponseService = new RpcResponseService(getRpcConnList(), meterRegistry);
            factory.registerSingleton("rpcResponseService", rpcResponseService);
        }
    }

    private void createNamespace(String name, KafkaRedisRpcItem item) {
        RpcNamespace ns = new RpcNamespace(name, item, ctx, meterRegistry);
        namespaces.put(name, ns);

        /* Create beans. TODO: use components from model holder instead of beans everywhere */
        if (ns.hasConsumer()) {
            factory.registerSingleton(RpcNameFactory.namespaceConsumer(name), ns.getKafkaConsumer());
            hasAnyConsumer = true;
        }
        if (ns.hasProducer()) {
            factory.registerSingleton(RpcNameFactory.namespaceProducer(name), ns.getKafkaProducer());
        }
        if (ns.hasRedis()) {
            factory.registerSingleton(RpcNameFactory.namespaceRedis(name), ns.getRedis());
            factory.registerSingleton(RpcNameFactory.namespaceConnection(name), ns.getRpcConn());
        }
        if (ns.hasRpcProducer()) {
            factory.registerSingleton(RpcNameFactory.namespaceSender(name), ns.getRpcProducer());
        }
    }

    /**
     * 2. Add topic for corresponding namespace.
     *
     * @param direction Producer or consumer topic
     * @param nsName Namespace name
     * @param topicName Topic name
     * @param transactional True if topic is transactional
     */
    public void addTopic(RpcDirection direction, String nsName, String topicName, boolean transactional) {
        ensureNamespace(nsName).createTopic(direction, topicName, transactional);
    }

    /**
     * 3. Add RPC listener bean to topic of corresponding namespace.
     *
     * @param nsName Namespace name
     * @param topicName Topic name
     * @param bean RPC bean
     */
    public void addListenerBean(String nsName, String topicName, Object bean) {
        ensureNamespace(nsName).addListenerBean(topicName, bean);
    }

    /**
     * 4. Finish configuration and start listeners
     */
    public void postProcess(ApplicationEvent event) {
        /* Create kafka listeners for each namespace */
        if (hasAnyConsumer) {
            namespaces().forEach(ns -> ns.createListener(rpcResponseService, event));
        }
    }

    public Collection<RpcNamespace> namespaces() {
        return namespaces.values();
    }

    public KafkaTemplate getProducer(String nsName) {
        return ensureNamespace(nsName).getKafkaProducer();
    }

    public boolean hasProducer(String nsName) {
        RpcNamespace ns = namespaces.get(nsName);
        if (ns == null) {
            return false;
        }
        return ns.hasProducer();
    }

    public void ensureConsumer(String nsName, String target) {
        if (!ensureNamespace(nsName).hasConsumer()) {
            throw new IllegalArgumentException("RPC consumer configuration is missing for " + target);
        }
    }

    private RpcNamespace ensureNamespace(String name) {
        RpcNamespace ns = namespaces.get(name);
        if (ns == null) {
            throw new IllegalArgumentException("Unknown RPC namespace: " + name + ". Missing properties?");
        }
        return ns;
    }

    private List<KafkaRpcConnection> getRpcConnList() {
        return namespaces().stream()
                .filter(ns -> ns.hasRedis())
                .map(ns -> ns.getRpcConn())
                .collect(Collectors.toList());
    }
}
