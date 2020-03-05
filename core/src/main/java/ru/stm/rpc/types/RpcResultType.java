package ru.stm.rpc.types;

import ru.stm.rpc.services.RpcService;

/**
 * Base RPC response. Needed for typing @see {@link RpcService}
 */
public interface RpcResultType {
    default void appendShort(StringBuilder sb) {
        sb.append(toString());
    }
}
