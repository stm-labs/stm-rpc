package ru.stm.rpc.services.stub;

import reactor.core.publisher.Mono;
import ru.stm.rpc.core.RpcResult;
import ru.stm.rpc.services.RpcService;
import ru.stm.rpc.types.RpcRequest;
import ru.stm.rpc.types.RpcResultType;

public class RpcServiceStub implements RpcService<RpcStubCtx> {

    @Override
    public <T extends RpcResultType, N extends RpcRequest> Mono<RpcResult<T>> callWithoutContext(N request, Class<T> result) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends RpcResultType, N extends RpcRequest> Mono<RpcResult<T>> callWithoutContext(N request, String topic, long timeout, Class<T> result) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends RpcResultType, N extends RpcRequest> Mono<RpcResult<T>> callWithoutContext(N request, String topic, Class<T> result) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends RpcResultType, N extends RpcRequest> Mono<RpcResult<T>> callWithoutContext(N request, String topic, String namespace, Class<T> result) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends RpcResultType, N extends RpcRequest> Mono<RpcResult<T>> callWithoutContext(N request, String topic, String namespace, long timeout, Class<T> result) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends RpcResultType, N extends RpcRequest> Mono<RpcResult<T>> call(RpcStubCtx context, N request, Class<T> result) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends RpcResultType, N extends RpcRequest> Mono<RpcResult<T>> call(RpcStubCtx context, N request, String topic, Class<T> result) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends RpcResultType, N extends RpcRequest> Mono<RpcResult<T>> call(RpcStubCtx context, N request, String topic, Long timeout, Class<T> result) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends RpcResultType, N extends RpcRequest> Mono<RpcResult<T>> call(RpcStubCtx context, N request, String topic, String namespace, Long timeout, Class<T> result) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends RpcResultType, N extends RpcRequest> Mono<RpcResult<T>> call(RpcStubCtx context, N request, String topic, String namespace, Class<T> result) {
        throw new UnsupportedOperationException();
    }
}
