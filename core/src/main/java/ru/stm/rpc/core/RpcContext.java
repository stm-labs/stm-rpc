package ru.stm.rpc.core;

public class RpcContext {

    // https://zipkin.io/pages/instrumenting.html

    public static final String KAFKA_USER_CONTEXT = "KAFKA_USER_CONTEXT";

    // in terminology "open tracing" - it is "Span Id"
    public static final String OPERATION_ID = "OPERATION_ID";

    // trace id
    public static final String TRACE_ID = "TRACE_ID";

    public static final String TIMEOUT = "TIMEOUT";

}
