package ru.stm.rpc.types;

import ru.stm.rpc.services.RpcService;

/**
 * Base RPC request. Needed for typing @see {@link RpcService}
 */
public interface RpcRequest {
    default void appendShort(StringBuilder sb) {
        sb.append(toString());
    }
}
