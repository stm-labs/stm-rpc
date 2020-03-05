package ru.stm.rpc.core;

import ru.stm.rpc.types.MethodType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation of java-method for rpc using
 * <p>
 * Arguments:
 * RpcRequest - payload (required),
 * RpcCtx - user context (optional)
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RpcHandler {
    /**
     * @return Short description of the method
     */
    String value() default "";

    /**
     * @return Method type
     */
    MethodType type() default MethodType.GET;

    /**
     * @return True if method is asynchronous (does not require response)
     */
    boolean async() default false;

    /**
     * @return True if Kafka acknowledgement shall be sent on error
     */
    boolean ackOnError() default true;
}
