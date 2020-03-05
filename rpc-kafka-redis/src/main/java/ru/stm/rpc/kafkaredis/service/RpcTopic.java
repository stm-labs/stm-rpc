package ru.stm.rpc.kafkaredis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.support.Acknowledgment;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import ru.stm.rpc.core.NoDefRpcCtx;
import ru.stm.rpc.core.RpcContext;
import ru.stm.rpc.core.RpcCtx;
import ru.stm.rpc.core.RpcHandler;
import ru.stm.rpc.kafkaredis.consumer.RpcAbstractRpcListener;
import ru.stm.rpc.kafkaredis.serialize.RpcSerializer;
import ru.stm.rpc.kafkaredis.util.RemoteServiceLogger;
import ru.stm.rpc.kafkaredis.util.RpcDirection;
import ru.stm.rpc.types.RpcRequest;
import ru.stm.rpc.types.RpcResultType;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static java.lang.System.currentTimeMillis;
import static ru.stm.rpc.core.RpcContext.KAFKA_USER_CONTEXT;
import static ru.stm.rpc.kafkaredis.config.KafkaRpcInternalConstants.KAFKA_REDIS_RPC_TIMEOUT;

@Slf4j
public class RpcTopic {

    private final static ObjectMapper objectMapper = RpcSerializer.getObjectMapper();

    private final String namespace;
    @Getter
    private final String topic;
    @Getter
    private final boolean transactional;
    private final Map<String, MethodDef> methods;

    public RpcTopic(String namespace, String topic, boolean transactional) {
        this.namespace = namespace;
        this.topic = topic;
        this.transactional = transactional;

        this.methods = new HashMap<>();
    }

    /**
     * Parse bean and map request classes to methods.
     *
     * @param bean Bean instance
     */
    @SuppressWarnings("unchecked")
    public void addListenerBean(Object bean) {
        Class<?> beanClass = bean.getClass();
        RemoteServiceLogger logger = new RemoteServiceLogger(beanClass, namespace, topic, RpcDirection.CONSUMER);

        for (Method m : beanClass.getMethods()) {
            /* Check that method is an RPC handler */
            RpcHandler handler = m.getAnnotation(RpcHandler.class);
            if (handler == null) {
                continue;
            }

            /* Check that return type is Mono */
            Class<?> returnClass = m.getReturnType();
            if (!returnClass.equals(Mono.class)) {
                throw new IllegalArgumentException("RpcHandler does not return Mono<>, class=" + beanClass.getName() + ", method=" + m.getName());
            }

            /* Check that return Mono is for RpcResultType unless async */
            if (!handler.async()) {
                log.trace("Processing class {} method {}", beanClass.getSimpleName(), m.getName());
                Type type = ((ParameterizedType) m.getGenericReturnType()).getActualTypeArguments()[0];
                if (type instanceof Class) {
                    Class<?> monoType = (Class<?>) type;
                    if (!RpcResultType.class.isAssignableFrom(monoType)) {
                        throw new IllegalArgumentException(String.format("RpcHandler shall return Mono<? extends RpcResultType>, class=%s, method=%s, monoType=%s",
                                beanClass.getName(), m.getName(), monoType.getName()));
                    }
                }
            }

            MethodDef def = new MethodDef();
            def.method = m;
            def.bean = bean;
            def.logger = logger;
            def.handler = handler;

            /* Scan for arguments */
            Class<?>[] paramTypes = m.getParameterTypes();
            for (int i = 0; i < paramTypes.length; i++) {
                Class<?> paramClass = paramTypes[i];
                if (RpcCtx.class.isAssignableFrom(paramClass)) {
                    def.hasContext = true;
                    def.contextIndex = i;
                    def.contextClass = paramClass.equals(RpcCtx.class)
                            ? NoDefRpcCtx.class
                            : (Class<? extends RpcCtx>) paramClass;
                } else if (!def.hasRequest) {
                    /* Check that parameter is RpcRequest */
                    if (RpcRequest.class.isAssignableFrom(paramClass)) {
                        def.hasRequest = true;
                        def.requestIndex = i;
                    }
                }
            }

            /* Check that request is present */
            if (!def.hasRequest) {
                throw new RuntimeException("RpcHandler does not have RpcRequest parameter, class=" + beanClass.getName() + ", method=" + m.getName());
            }

            /* Add method and check that no other method handles this request class */
            MethodDef old = methods.put(paramTypes[def.requestIndex].getName(), def);
            if (old != null) {
                throw new IllegalStateException(String.format("RPC handler %s#%s conflicts with %s#%s",
                        def.bean.getClass().getSimpleName(), def.method.getName(),
                        old.bean.getClass().getSimpleName(), old.method.getName()));
            }
        }
    }

    /**
     * Handle incoming request for this topic.
     *
     * @param data        Record from Kafka topic
     * @param rpcListener RpcListener for this namespace
     * @param ack         Kafka record acknowledgement
     */
    public void handle(ConsumerRecord<String, String> data, RpcAbstractRpcListener rpcListener, Acknowledgment ack) {
        RpcRequest req = (RpcRequest) (Object) data.value();

        /* Check if request class is supported */
        MethodDef def = methods.get(req.getClass().getName());
        if (def == null) {
            throw new UnsupportedOperationException(String.format("Unsupported RPC request class %s, ns=%s, topic=%s",
                    req.getClass().getName(), namespace, topic));
        }

        /* Get user context if present */
        byte[] authContext = getSingleHeaderOrNull(data, KAFKA_USER_CONTEXT);
        RpcCtx ctx = def.hasContext ? getUserContext(authContext, def.contextClass) : null;

        /* Check if this is sync or async request */
        if (def.handler.async()) {
            invokeAsync(def, req, ctx, ack);
        } else {
            invokeSync(def, req, ctx, rpcListener, ack, data);
        }
    }

    private void invokeAsync(MethodDef def, Object req, RpcCtx ctx, Acknowledgment ack) {
        invoke(def, req, ctx)
                .doOnSuccess(rsp -> ack.acknowledge())
                .switchIfEmpty(Mono.defer(() -> {
                    ack.acknowledge();
                    return Mono.empty();
                }))
                .doOnError(err -> {
                    /* If RpcHandler specifies acknowledging on error, do it */
                    if (def.handler.ackOnError()) {
                        ack.acknowledge();
                    }
                })
                .subscribe();
    }

    private void invokeSync(MethodDef def, RpcRequest req, RpcCtx ctx, RpcAbstractRpcListener rpcListener, Acknowledgment ack, ConsumerRecord<String, String> data) {
        OffsetDateTime timeout = null;
        Iterator<Header> iterator = data.headers().headers(KAFKA_REDIS_RPC_TIMEOUT).iterator();
        if (iterator.hasNext()) {
            timeout = OffsetDateTime.parse(new String(iterator.next().value()));
        }

        OffsetDateTime finalTimeout = timeout;
        String opId = data.key();

        byte[] trace = getSingleHeaderOrNull(data, RpcContext.TRACE_ID);
        String traceId = trace == null ? "NO_TRACE_ID" : new String(trace);

        rpcListener.executeAndSend(opId, traceId, req, ack,
                () -> this.<RpcResultType>invoke(def, req, ctx)
                        .subscriberContext(Context.of(
                                RpcContext.OPERATION_ID, opId,
                                RpcContext.TIMEOUT, finalTimeout,
                                RpcContext.TRACE_ID, traceId)),
                finalTimeout
        );
    }

    @SuppressWarnings("unchecked")
    private <R> Mono<R> invoke(MethodDef def, Object req, RpcCtx ctx) {
        try {
            /* Build parameters */
            Object[] parameters = new Object[def.method.getParameterCount()];
            parameters[def.requestIndex] = req;
            if (def.hasContext) {
                parameters[def.contextIndex] = ctx;
            }

            /* Save current nanoseconds */
            long millis = currentTimeMillis();

            try {
                /* Invoke method */
                Mono<R> mono = (Mono<R>) def.method.invoke(def.bean, parameters);
                if (mono == null) {
                    return Mono.error(new IllegalStateException("Method returned no mono: " + def.method.getName()));
                }

                /* Log result and return mono */
                return mono.doOnSuccessOrError((rsp, error) -> {
                    def.logger.log(def.handler.value(), def.handler.type(), def.method.getName(), ctx, req, rsp, error, currentTimeMillis() - millis);
                }).switchIfEmpty(Mono.defer(() -> {
                    def.logger.log(def.handler.value(), def.handler.type(), def.method.getName(), ctx, req, null, null, currentTimeMillis() - millis);
                    return Mono.empty();
                }));
            } catch (InvocationTargetException e) {
                def.logger.log(def.handler.value(), def.handler.type(), def.method.getName(), ctx, req, null, e, currentTimeMillis() - millis);
                return Mono.error(e.getCause());
            }
        } catch (Throwable e) {
            return Mono.error(e);
        }
    }

    private RpcCtx getUserContext(byte[] userContext, Class<? extends RpcCtx> clazz) {
        if (userContext == null) {
            return null;
        }
        try {
            return objectMapper.readValue(userContext, clazz);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] getSingleHeaderOrNull(ConsumerRecord data, String headerName) {
        if (data.headers().headers(headerName).iterator().hasNext()) {
            return data.headers().headers(headerName).iterator().next().value();
        }
        return null;
    }

    private static class MethodDef {
        private Method method;
        private Object bean;
        private RemoteServiceLogger logger;
        private RpcHandler handler;
        private boolean hasRequest;
        private boolean hasContext;
        private int requestIndex;
        private int contextIndex;
        private Class<? extends RpcCtx> contextClass;
    }
}
