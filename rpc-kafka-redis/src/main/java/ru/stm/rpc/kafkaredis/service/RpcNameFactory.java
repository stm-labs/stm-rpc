package ru.stm.rpc.kafkaredis.service;

import io.micrometer.core.instrument.util.StringUtils;

public class RpcNameFactory {

    private static final String RPC_PREFIX = "rpc";

    public static String modelHolder() {
        return "rpcModelHolder";
    }

    public static String namespaceConsumer(String namespace) {
        return RPC_PREFIX + toUpper(namespace) + "Consumer";
    }

    public static String namespaceConnection(String namespace) {
        return RPC_PREFIX + toUpper(namespace) + "Connection";
    }

    public static String namespaceProducer(String namespace) {
        return RPC_PREFIX + toUpper(namespace) + "Producer";
    }

    public static String namespaceSender(String namespace) {
        return RPC_PREFIX + toUpper(namespace) + "Sender";
    }

    public static String namespaceRedis(String namespace) {
        return RPC_PREFIX + toUpper(namespace) + "Redis";
    }

    private static String toUpper(String value) {
        assert !StringUtils.isBlank(value);

        String s = value.trim();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
