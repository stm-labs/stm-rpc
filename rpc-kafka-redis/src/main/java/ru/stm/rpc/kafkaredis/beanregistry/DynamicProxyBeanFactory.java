package ru.stm.rpc.kafkaredis.beanregistry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cglib.proxy.Proxy;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import ru.stm.rpc.kafkaredis.ann.RemoteService;
import ru.stm.rpc.kafkaredis.beanregistry.handler.DynamicProxyInvocationHandlerDispatcher;
import ru.stm.rpc.kafkaredis.service.RpcProvider;

import java.util.Arrays;

import static ru.stm.rpc.kafkaredis.beanregistry.DynamicProxyBeanFactory.DYNAMIC_PROXY_BEAN_FACTORY;

@Slf4j
@RequiredArgsConstructor
@Component(DYNAMIC_PROXY_BEAN_FACTORY)
public class DynamicProxyBeanFactory {
    public static final String DYNAMIC_PROXY_BEAN_FACTORY = "repositoryProxyBeanFactory";
    private final RpcBeanRegistry rpcBeanRegistry;
    private final RpcTopicParser rpcTopicParser;

    @SuppressWarnings("unused")
    public <T> T createDynamicProxyBean(Class<T> beanClass) {
        RemoteService remoteService = beanClass.getAnnotation(RemoteService.class);
        RpcProvider rpcProvider = rpcBeanRegistry.getRpcProvider(remoteService);
        DynamicProxyInvocationHandlerDispatcher proxy = new DynamicProxyInvocationHandlerDispatcher(rpcProvider);

        log.debug("Create Remote Service: {}, namespaces = {}, topic = {}",
                ClassUtils.getShortName(beanClass.getSimpleName()),
                Arrays.asList(remoteService.namespace()),
                rpcTopicParser.parse(remoteService));

        //noinspection unchecked
        return (T) Proxy.newProxyInstance(beanClass.getClassLoader(), new Class[]{beanClass}, proxy);
    }
}
