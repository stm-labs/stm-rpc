package ru.stm.rpc.core;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class RpcForwardingException extends RuntimeException {

    private final String operationId;

    public RpcForwardingException(String message, String operationId) {
        super(message);
        this.operationId = operationId;
    }

}
