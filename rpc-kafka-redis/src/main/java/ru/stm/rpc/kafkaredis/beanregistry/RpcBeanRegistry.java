package ru.stm.rpc.kafkaredis.beanregistry;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import ru.stm.rpc.core.Rpc;
import ru.stm.rpc.kafkaredis.ann.RemoteInterface;
import ru.stm.rpc.kafkaredis.ann.RemoteService;
import ru.stm.rpc.kafkaredis.beanregistry.propsparse.StmConfigurationPropertiesBindingPostProcessor;
import ru.stm.rpc.kafkaredis.config.KafkaRedisRpcProperties;
import ru.stm.rpc.kafkaredis.service.RpcModelHolder;
import ru.stm.rpc.kafkaredis.service.RpcNameFactory;
import ru.stm.rpc.kafkaredis.service.RpcProvider;
import ru.stm.rpc.kafkaredis.topic.KafkaEnsureTopicHelper;
import ru.stm.rpc.kafkaredis.util.RemoteInterfaceProxy;
import ru.stm.rpc.kafkaredis.util.RemoteServiceLogger;
import ru.stm.rpc.kafkaredis.util.RpcDirection;

import java.util.Arrays;

@Component
@Slf4j
public class RpcBeanRegistry implements BeanDefinitionRegistryPostProcessor, ApplicationContextAware, BeanPostProcessor, InitializingBean {

    private ApplicationContext appCtx;
    private KafkaRedisRpcProperties rpcProps;
    private RpcModelHolder holder;
    private RpcTopicParser rpcTopicParser;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.appCtx = applicationContext;
    }

    /**
     * 1. Parse RPC configuration properties
     */
    @Override
    public void afterPropertiesSet() {
        StmConfigurationPropertiesBindingPostProcessor stmConfigurationPropertiesBindingPostProcessor = new StmConfigurationPropertiesBindingPostProcessor(appCtx);
        rpcProps = (KafkaRedisRpcProperties) stmConfigurationPropertiesBindingPostProcessor.postProcessBeforeInitialization(appCtx.getBean(KafkaRedisRpcProperties.class));
        rpcTopicParser = appCtx.getBean(RpcTopicParser.class);
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry beanDefinitionRegistry) throws BeansException {

    }

    /**
     * 2. Process all bean definitions for classes annotated with @Rpc, @RpcAsync and @RemoteService, and build model tree.
     *
     * @param factory Bean factory
     * @throws BeansException in case of errors
     */
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory factory) throws BeansException {
        if (rpcProps == null || rpcProps.getNamespace() == null) {
            log.warn("rpcProps not found");
            return;
        }

        log.debug("Creating RPC model");

        /* Create and register holder with all RPC models and components */
        holder = new RpcModelHolder(factory, appCtx, rpcProps);

        factory.registerSingleton(RpcNameFactory.modelHolder(), holder);

        /* Scan beans for RPC annotations */
        for (String name : factory.getBeanDefinitionNames()) {
            BeanDefinition def = factory.getBeanDefinition(name);

            /* Scan for RemoteService components and set constructor arguments */
            RemoteService remoteService = factory.findAnnotationOnBean(name, RemoteService.class);
            if (remoteService != null) {
                /* Add topic only if producer is configured for this namespace */
                if (holder.hasProducer(remoteService.namespace())) {
                    log.debug("Found Remote Service: {}, namespaces = {}, topic = {}",
                            ClassUtils.getShortName(def.getBeanClassName()),
                            Arrays.asList(remoteService.namespace()),
                            rpcTopicParser.parse(remoteService));

                    holder.addTopic(RpcDirection.PRODUCER, remoteService.namespace(), rpcTopicParser.parse(remoteService), remoteService.transactional());

                    /* Add RpcProvider with topic and producer inside */
                    def.getConstructorArgumentValues().addGenericArgumentValue(
                            new RpcProvider(appCtx, rpcTopicParser.parse(remoteService), remoteService.namespace(),
                                    holder.getProducer(remoteService.namespace())));
                } else {
                    log.trace("Skipping Remote Service: {}, namespaces = {}, topic = {}",
                            ClassUtils.getShortName(def.getBeanClassName()),
                            Arrays.asList(remoteService.namespace()),
                            rpcTopicParser.parse(remoteService));

                    /* Remove bean as it is not needed */
                    BeanDefinitionRegistry registry = (BeanDefinitionRegistry) factory;
                    if (registry.containsBeanDefinition(name)) {
                        registry.removeBeanDefinition(name);
                        registry.registerAlias(RpcNameFactory.modelHolder(), name);
                    }
                }
            }

            /* Scan for Rpc components */
            Rpc rpc = factory.findAnnotationOnBean(name, Rpc.class);
            if (rpc != null && !rpc.namespace().isEmpty()) {
                log.debug("Found RPC Handler: {}, namespace = {}, topic = {}",
                        ClassUtils.getShortName(def.getBeanClassName()),
                        rpc.namespace(),
                        rpcTopicParser.parse(rpc));

                holder.ensureConsumer(rpc.namespace(), def.getBeanClassName());
                holder.addTopic(RpcDirection.CONSUMER, rpc.namespace(), rpcTopicParser.parse(rpc), rpc.transactional());
            }
        }

        /* Create topics in Kafka for all namespaces */
        KafkaEnsureTopicHelper.handleTopics(holder.namespaces());
    }

    /**
     * 3. Process @RpcHandle and @RemoteMethod for all beans annotated with @Rpc, @RpcAsync and @RemoteService.
     *
     * @param bean     Bean instance
     * @param beanName Bean name
     * @return Proxy instance for @RemoteService, the same bean otherwise
     * @throws BeansException in case of errors
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> beanClass = bean.getClass();

        /* Scan for Remote Service beans */
        RemoteService remoteService = beanClass.getAnnotation(RemoteService.class);
        if (remoteService != null) {
            /* Check that it implements Remote Interface */
            Class<?> remoteInterface = findRemoteInterface(beanClass);
            if (remoteInterface == null) {
                return bean;
            }

            /* Create logger for service */
            RemoteServiceLogger logger = new RemoteServiceLogger(beanClass, remoteService.namespace(), rpcTopicParser.parse(remoteService), RpcDirection.PRODUCER);

            /* Create proxy handler */
            RemoteInterfaceProxy handler = new RemoteInterfaceProxy(bean, remoteInterface, logger);

            /* Create and return proxy */
            Enhancer en = new Enhancer();
            en.setSuperclass(beanClass);
            en.setInterfaces(new Class<?>[]{remoteInterface});
            en.setCallback(handler);
            Class<?>[] paramTypes = beanClass.getConstructors()[0].getParameterTypes();
            return en.create(paramTypes, new Object[paramTypes.length]);
        }

        /* Scan for Rpc components */
        Rpc rpc = beanClass.getAnnotation(Rpc.class);
        if (rpc != null && !rpc.namespace().isEmpty()) {
            /* Add listener bean to topic */
            holder.addListenerBean(rpc.namespace(), rpcTopicParser.parse(rpc), bean);
        }

        return bean;
    }

    private final Class<?> findRemoteInterface(Class<?> clazz) {
        for (Class<?> intClass : clazz.getInterfaces()) {
            if (intClass.getAnnotation(RemoteInterface.class) != null) {
                return intClass;
            }
        }
        return null;
    }

    /**
     * 4. Finish configuration and start all Kafka listeners.
     *
     * @param event Context initialized event
     */
    @EventListener
    public void onApplicationEvent(ContextRefreshedEvent event) {
        holder.postProcess(event);
    }
}
