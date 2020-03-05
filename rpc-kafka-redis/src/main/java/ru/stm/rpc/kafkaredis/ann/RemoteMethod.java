package ru.stm.rpc.kafkaredis.ann;

import ru.stm.rpc.types.MethodType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RemoteMethod {
    /**
     * @return Method description
     */
    String value();

    /**
     * @return Method type (GET, POST, INTERNAL)
     */
    MethodType type() default MethodType.GET;
}
