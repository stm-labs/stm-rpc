package ru.stm.rpc.kafkaredis.beanregistry.handler;

import org.springframework.cglib.proxy.InvocationHandler;

public interface ProxyInvocationHandler extends InvocationHandler, HandlerMatcher {
}
