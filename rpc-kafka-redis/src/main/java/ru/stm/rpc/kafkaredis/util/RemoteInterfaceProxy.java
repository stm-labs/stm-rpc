package ru.stm.rpc.kafkaredis.util;

import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;
import reactor.core.publisher.Mono;
import ru.stm.rpc.core.RpcCtx;
import ru.stm.rpc.kafkaredis.ann.RemoteMethod;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.System.currentTimeMillis;

public class RemoteInterfaceProxy implements MethodInterceptor {

    private final Object bean;
    private final RemoteServiceLogger logger;
    private final Map<String, MethodDef> methods;

    public RemoteInterfaceProxy(Object bean, Class<?> remoteInterface, RemoteServiceLogger logger) {
        this.bean = bean;
        this.logger = logger;
        this.methods = new ConcurrentHashMap<>();

        scanMethods(remoteInterface);
    }

    private void scanMethods(Class<?> remoteInterface) {
        for (Method m : remoteInterface.getMethods()) {
            /* Check that method is a remote method */
            RemoteMethod remoteMethod = m.getAnnotation(RemoteMethod.class);
            if (remoteMethod == null) {
                continue;
            }

            /* Check that return type is Mono */
            Class<?> returnType = m.getReturnType();
            if (!returnType.isAssignableFrom(Mono.class)) {
                continue;
            }

            MethodDef def = new MethodDef();
            def.remoteMethod = remoteMethod;
            methods.put(m.getName(), def);

            /* Scan for arguments */
            Class<?>[] paramTypes = m.getParameterTypes();
            for (int i = 0; i < paramTypes.length; i++) {
                Class<?> pt = paramTypes[i];
                if (pt.isAssignableFrom(RpcCtx.class)) {
                    def.hasContext = true;
                    def.contextIndex = i;
                } else if (!def.hasRequest) {
                    def.hasRequest = true;
                    def.requestIndex = i;
                }
            }
        }
    }

    @Override
    public Object intercept(Object o, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
        try {
            /* Check if this is remote method */
            MethodDef def = methods.get(method.getName());
            if (def == null) {
                return method.invoke(bean, args);
            }

            /* Fetch arguments */
            RpcCtx ctx = def.hasContext ? (RpcCtx) args[def.contextIndex] : null;
            Object req = def.hasRequest ? args[def.requestIndex] : null;

            /* Save current nanoseconds */
            long millis = currentTimeMillis();

            try {
                /* Invoke method */
                Mono<?> mono = (Mono<?>) method.invoke(bean, args);
                if (mono == null) {
                    return Mono.error(new RuntimeException("Method returned no mono: " + method.getName()));
                }

                /* Log result and return mono */
                return mono.doOnSuccessOrError((rsp, error) -> {
                    logger.log(def.remoteMethod.value(), def.remoteMethod.type(), method.getName(), ctx, req, rsp, error, currentTimeMillis() - millis);
                }).switchIfEmpty(Mono.defer(() -> {
                    logger.log(def.remoteMethod.value(), def.remoteMethod.type(), method.getName(), ctx, req, null, null, currentTimeMillis() - millis);
                    return Mono.empty();
                }));
            } catch (InvocationTargetException e) {
                logger.log(def.remoteMethod.value(), def.remoteMethod.type(), method.getName(), ctx, req, null, e.getCause(), currentTimeMillis() - millis);
                return Mono.error(e.getCause());
            }
        } catch (Throwable e) {
            return Mono.error(e);
        }
    }

    private static class MethodDef {
        private RemoteMethod remoteMethod;
        private boolean hasRequest;
        private boolean hasContext;
        private int requestIndex;
        private int contextIndex;
    }
}
