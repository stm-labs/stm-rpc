package ru.stm.rpc.kafkaredis.beanregistry.handler;

import java.lang.reflect.Method;

public interface HandlerMatcher {
    /**
     * @return {@code true} if handler is able to handle given method, {@code false} othervise
     */
    boolean canHandle(Method method);
}

