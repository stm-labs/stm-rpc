package ru.stm.rpc.services;

import reactor.core.publisher.Mono;
import ru.stm.rpc.core.RpcCtx;
import ru.stm.rpc.core.RpcResult;
import ru.stm.rpc.types.RpcRequest;
import ru.stm.rpc.types.RpcResultType;

/**
 * RPC Producer
 */
public interface RpcService<M extends RpcCtx> {

    /**
     * Call RPC without user context
     *
     * @param request
     * @param result  result class
     * @return RPC call result
     * @Deprecated
     */
    <T extends RpcResultType, N extends RpcRequest> Mono<RpcResult<T>> callWithoutContext(N request, Class<T> result);

    <T extends RpcResultType, N extends RpcRequest> Mono<RpcResult<T>> callWithoutContext(N request, String topic,
                                                                                          long timeout, Class<T> result);

    <T extends RpcResultType, N extends RpcRequest> Mono<RpcResult<T>> callWithoutContext(N request, String topic,
                                                                                          Class<T> result);

    /**
     * Call RPC with user context
     *
     * @param context user context
     * @param request
     * @param result  result class
     * @return RPC call result
     */
    <T extends RpcResultType, N extends RpcRequest> Mono<RpcResult<T>> call(M context, N request, Class<T> result);

    /**
     * Call RPC into specific namespace
     *
     * @param context user context
     * @param request
     * @param topic   specific namespace
     * @param result  result class
     * @return RPC call result
     */
    <T extends RpcResultType, N extends RpcRequest> Mono<RpcResult<T>> call(M context, N request, String topic, Class<T> result);

    /**
     * Call RPC into specific namespace with timeout
     *
     * @param context user context
     * @param request
     * @param topic   specific namespace
     * @param timeout
     * @param result  result class
     * @return RPC call result
     */
    <T extends RpcResultType, N extends RpcRequest> Mono<RpcResult<T>> call(M context, N request, String topic, Long timeout, Class<T> result);

}
