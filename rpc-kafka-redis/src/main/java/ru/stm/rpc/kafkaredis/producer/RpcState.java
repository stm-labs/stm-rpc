package ru.stm.rpc.kafkaredis.producer;

import lombok.Getter;
import reactor.core.publisher.MonoSink;

import java.time.OffsetDateTime;

/**
 * RPC request state
 */
@Getter
class RpcState {

    // where response will be returned
    private final MonoSink handler;

    // return class
    private final Class returnClass;

    // current status
    private volatile RpcStatus status;

    private final OffsetDateTime finalTimeout;

    public RpcState(MonoSink handler, Class returnClass, RpcStatus status, OffsetDateTime finalTimeout) {
        this.handler = handler;
        this.returnClass = returnClass;
        this.status = status;
        this.finalTimeout = finalTimeout;
    }

    public void setStatus(RpcStatus status) {
        this.status = status;
    }
}
