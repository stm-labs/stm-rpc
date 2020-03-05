package ru.stm.rpc.kafkaredis.serialize;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

/**
 * Serialization/Deserialization for RPC
 * - Request to Kafka,
 * - Response to Redis
 */
//Todo: https://github.com/EsotericSoftware/kryo or protobuf can be used instead of json?
public class RpcSerializer {

    private final static ObjectMapper objectMapper;

    private static void setupObjectMapper(ObjectMapper o) {
        o.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        o.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        o.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

        o.registerModule(new ParameterNamesModule())
                .registerModule(new Jdk8Module())
                .registerModule(new JavaTimeModule()); // new module, NOT JSR310Module

        o.configure(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false);
    }

    static {
        ObjectMapper objectMapperTemp = new ObjectMapper();
        setupObjectMapper(objectMapperTemp);
        objectMapper = objectMapperTemp;
    }

    /**
     * @return common objectmapper for serialization and deserialization
     */
    public static ObjectMapper getObjectMapper() {
        return objectMapper;
    }

}
