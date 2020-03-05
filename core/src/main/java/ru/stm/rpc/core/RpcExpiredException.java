package ru.stm.rpc.core;

import java.time.OffsetDateTime;

public class RpcExpiredException extends RuntimeException {

    public RpcExpiredException(String msg) {
        super(msg);
    }

    public static RpcExpiredException create(String operationName, String key, String traceId, OffsetDateTime timeout) {
        String msg = String.format("RPC call %s of operation %s trace %s processing would not be started because timeout expired %s",
                operationName, key, traceId, timeout.toString());
        return new RpcExpiredException(msg);
    }

}