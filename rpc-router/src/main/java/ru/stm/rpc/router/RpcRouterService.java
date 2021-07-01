package ru.stm.rpc.router;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import ru.stm.rpc.core.RpcCtx;
import ru.stm.rpc.core.RpcResult;
import ru.stm.rpc.router.configuration.RpcRouterProperties;
import ru.stm.rpc.services.RpcService;
import ru.stm.rpc.services.RpcServiceRoute;
import ru.stm.rpc.types.RpcRequest;
import ru.stm.rpc.types.RpcResultType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class RpcRouterService<E extends RpcCtx> implements RpcService<E> {

    private Map<String, RpcServiceRoute> routes = new HashMap<>();
    private final RpcRouterProperties rpcRouterProperties;

    public RpcRouterService(@Autowired(required = false) List<RpcServiceRoute> allRoutes, RpcRouterProperties rpcRouterProperties) {
        this.rpcRouterProperties = rpcRouterProperties;
        if (allRoutes == null || allRoutes.isEmpty()) {
            log.info("There is not RPC producer");
            return;
        }
        allRoutes.forEach(x -> routes.put(getTokenForDestination(x.getName(), x.getNamespace()), x));
    }

    @Override
    public <T extends RpcResultType, N extends RpcRequest> Mono<RpcResult<T>> callWithoutContext(N request, Class<T> result) {
        return call(null, request, result);
    }

    @Override
    public <T extends RpcResultType, N extends RpcRequest> Mono<RpcResult<T>> callWithoutContext(N request, String topic, Class<T> result) {
        return call(null, request, topic, result);
    }

    @Override
    public <T extends RpcResultType, N extends RpcRequest> Mono<RpcResult<T>> callWithoutContext(N request, String topic, String namespace, Class<T> result) {
        return call(null, request, topic, namespace, null, result);
    }

    @Override
    public <T extends RpcResultType, N extends RpcRequest> Mono<RpcResult<T>> callWithoutContext(N request, String topic, String namespace, long timeout, Class<T> result) {
        return null;
    }

    @Override
    public <T extends RpcResultType, N extends RpcRequest> Mono<RpcResult<T>> callWithoutContext(N request, String topic, long timeout, Class<T> result) {
        return call(null, request, topic, timeout, result);
    }

    @Override
    public <T extends RpcResultType, N extends RpcRequest> Mono<RpcResult<T>> call(E context, N request, Class<T> result) {
        return call(context, request, rpcRouterProperties.getDef().getTopic(), result);
    }

    @Override
    public <T extends RpcResultType, N extends RpcRequest> Mono<RpcResult<T>> call(E context, N request, String topic, Class<T> result) {
        return call(context, request, topic, null, null, result);
    }

    @Override
    public <T extends RpcResultType, N extends RpcRequest> Mono<RpcResult<T>> call(E context, N request, String topic, Long timeout, Class<T> result) {
        return call(context, request, topic, null, null, result);
    }

    @Override
    public <T extends RpcResultType, N extends RpcRequest> Mono<RpcResult<T>> call(E context, N request, String topic, String namespace, Long timeout, Class<T> result) {
        RpcRouterProperties.RpcRouterRoute rpcTopicInfo = null;

        if (rpcRouterProperties.getRoute() != null) {
            rpcTopicInfo = rpcRouterProperties.getRoute().get(topic);
        }

        RpcServiceRoute<E> rpcServiceRoute;

        if (rpcTopicInfo == null) {
            String defDestination = rpcRouterProperties.getDef().getDestination();

            String realNamespace;

            if (!StringUtils.isEmpty(namespace)) {
                realNamespace = namespace;
            } else {
                realNamespace = rpcRouterProperties.getDef().getNamespace();
            }

            if (StringUtils.isEmpty(defDestination)) {
                throw new RuntimeException(String.format("Empty default destination for topic %s", topic));
            }

            if (StringUtils.isEmpty(realNamespace)) {
                throw new RuntimeException(String.format("Empty default namespace for topic %s", topic));
            }

            rpcServiceRoute = routes.get(getTokenForDestination(defDestination, realNamespace));
        } else {
            rpcServiceRoute = routes.get(getTokenForDestination(rpcTopicInfo.getDestination(), rpcTopicInfo.getNamespace()));
        }

        if (rpcServiceRoute == null) {
            throw new RuntimeException(String.format("No route for topic %s", topic));
        }

        if (timeout == null) {
            return rpcServiceRoute.call(context, request, topic, result);
        } else {
            return rpcServiceRoute.call(context, request, topic, timeout, result);
        }

    }

    @Override
    public <T extends RpcResultType, N extends RpcRequest> Mono<RpcResult<T>> call(E context, N request, String topic, String namespace, Class<T> result) {
        return call(context, request, topic, namespace, null, result);
    }

    private String getTokenForDestination(String destination, String namespace) {
        return destination + "#" + namespace;
    }

}
