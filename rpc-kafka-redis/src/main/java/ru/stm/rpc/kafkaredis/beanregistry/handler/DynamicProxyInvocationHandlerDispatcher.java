package ru.stm.rpc.kafkaredis.beanregistry.handler;

import lombok.SneakyThrows;
import org.springframework.cglib.proxy.InvocationHandler;
import org.springframework.stereotype.Component;
import ru.stm.rpc.kafkaredis.service.RpcProvider;

import java.lang.reflect.Method;

public class DynamicProxyInvocationHandlerDispatcher implements InvocationHandler {
    private final RpcProvider rpcProvider;


    public DynamicProxyInvocationHandlerDispatcher(RpcProvider rpcProvider) {
        this.rpcProvider = rpcProvider;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        switch (method.getName()) {
            // three Object class methods don't have default implementation after creation with Proxy::newProxyInstance
            case "hashCode":
                return System.identityHashCode(proxy);
            case "toString":
                return proxy.getClass() + "@" + System.identityHashCode(proxy);
            case "equals":
                return proxy == args[0];
            default:
                return doInvoke(proxy, method, args);
        }
    }

    @SneakyThrows
    private Object doInvoke(Object proxy, Method method, Object[] args) {
        return findHandler(method).invoke(proxy, method, args);
    }

    private ProxyInvocationHandler findHandler(Method method) {
        return new ProxyInvocationHandler() {
            @Override
            public Object invoke(Object o, Method method, Object[] objects) throws Throwable {
                return null;
            }

            @Override
            public boolean canHandle(Method method) {
                return true;
            }
        };
    }
}
