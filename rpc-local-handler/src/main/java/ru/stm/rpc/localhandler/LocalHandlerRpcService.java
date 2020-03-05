package ru.stm.rpc.localhandler;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import ru.stm.platform.BusinessError;
import ru.stm.rpc.core.Rpc;
import ru.stm.rpc.core.RpcCtx;
import ru.stm.rpc.core.RpcHandler;
import ru.stm.rpc.core.RpcResult;
import ru.stm.rpc.services.RpcServiceRoute;
import ru.stm.rpc.types.RpcRequest;
import ru.stm.rpc.types.RpcResultType;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Slf4j
public class LocalHandlerRpcService<E extends RpcCtx> implements BeanPostProcessor, RpcServiceRoute<E> {
    // TODO: use method reference
    private HashMap<String, RpcCallContainer> handlers = new HashMap<>();

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        boolean annotationPresent = bean.getClass().isAnnotationPresent(Rpc.class);
        if (annotationPresent) {
            String topic = bean.getClass().getAnnotation(Rpc.class).topic();
            log.info("Found Rpc {}", topic);

            List<Method> methods = Arrays.stream(bean.getClass().getMethods())
                    .filter(x -> AnnotationUtils.findAnnotation(x, RpcHandler.class) != null).collect(Collectors.toList());
            for (Method method : methods) {
                Class type = (Class) method.getParameters()[0].getParameterizedType();
                handlers.put(getKey(type, topic), new RpcCallContainer(method, bean));
            }
        }

        return bean;
    }

    @Override
    public <T extends RpcResultType, N extends RpcRequest> Mono<RpcResult<T>> callWithoutContext(N request, String namespace, long timeout, Class<T> result) {
        return call(null, request, namespace, timeout, result);
    }

    @Override
    public <T extends RpcResultType, N extends RpcRequest> Mono<RpcResult<T>> callWithoutContext(N request, String namespace, Class<T> result) {
        return call(null, request, namespace, result);
    }

    @Override
    public <T extends RpcResultType, N extends RpcRequest> Mono<RpcResult<T>> call(E context, N request, String namespace, Class<T> result) {
        return call(context, request, namespace, null, result);
    }

    @Override
    public <T extends RpcResultType, N extends RpcRequest> Mono<RpcResult<T>> call(E context, N request, String namespace, Long timeout, Class<T> result) {
        return Mono.create(f -> {
            String operationId = UUID.randomUUID().toString();
            String key = getKey(request, namespace);

            RpcCallContainer container = handlers.get(key);
            if (container != null) {
                try {
                    Mono<T> invoke = (Mono<T>) callContainerMethod(container, request, context);
                    invoke.subscribe((ok) -> {
                        f.success(RpcResult.success(operationId, ok));
                    }, (err) -> {
                        if (err instanceof BusinessError) {
                            f.success(RpcResult.errorBusinessString(operationId, ((BusinessError) err).getCode(), err.getMessage(), ((BusinessError) err).getArgs()));
                        } else {
                            f.error(err);
                        }
                    });

                } catch (IllegalAccessException e) {
                    log.error("Error in get RPC event", e);
                    f.error(e);
                } catch (InvocationTargetException e) {
                    // Propagate thrown exception on upper layer
                    f.error(e.getCause());
                }
            } else {
                log.error("Not found method for {}", key);
                f.error(new RuntimeException("Not found method for " + key));
            }

        });
    }

    @Override
    public String getName() {
        return "LOCAL_HANDLER";
    }

    @Override
    public String getNamespace() {
        return "";
    }

    private <T extends RpcResultType, N extends RpcRequest> Mono<T> callContainerMethod(RpcCallContainer container, N request, E context)
            throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        if (container.method.getParameterCount() < 2) {
            return (Mono<T>) container.method.invoke(container.obj, request);
        }
        return (Mono<T>) container.method.invoke(container.obj, request, context);
    }

    private <N extends RpcRequest> String getKey(N request, String namespace) {
        return namespace + "###" + request.getClass().getName();
    }

    private String getKey(Class<?> request, String namespace) {
        return namespace + "###" + request.getName();
    }

    @Data
    @AllArgsConstructor
    public static class RpcCallContainer {

        private final Method method;
        private final Object obj;

    }

}