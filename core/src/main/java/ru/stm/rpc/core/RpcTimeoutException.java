package ru.stm.rpc.core;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class RpcTimeoutException extends RuntimeException {

    private final String operationId;
    private final String traceId;

    public RpcTimeoutException(String operationId, String traceId, String type, String message) {
        super(String.format("RPC Operation ID=%s traceId=%s type=%s message=%s", operationId, traceId, type, message));
        this.operationId = operationId;
        this.traceId = traceId;
    }

}
