package ru.stm.rpc.kafkaredis.serialize;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.ExtendedDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.converter.AbstractJavaTypeMapper;
import org.springframework.kafka.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.kafka.support.converter.Jackson2JavaTypeMapper;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

/**
 * Deserializer for kafka, based on org.springframework.kafka.support.serializer JsonDeserializer
 * without {@link org.apache.kafka.common.errors.SerializationException} throwing from infinite loop
 * if the record deserialization is failed
 */
public class KafkaJsonDeserializer<T> implements ExtendedDeserializer<T> {

    private static final Logger logger = LoggerFactory.getLogger(KafkaJsonDeserializer.class);

    public KafkaJsonDeserializer() {
        super();
        addTrustedPackages("*");
    }

    /**
     * Kafka config property for the default key type if no header.
     *
     * @deprecated in favor of {@link #KEY_DEFAULT_TYPE}
     */
    @Deprecated
    public static final String DEFAULT_KEY_TYPE = "spring.json.key.default.type";

    /**
     * Kafka config property for the default value type if no header.
     *
     * @deprecated in favor of {@link #VALUE_DEFAULT_TYPE}
     */
    @Deprecated
    public static final String DEFAULT_VALUE_TYPE = "spring.json.default.value.type";

    /**
     * Kafka config property for the default key type if no header.
     */
    public static final String KEY_DEFAULT_TYPE = "spring.json.key.default.type";

    /**
     * Kafka config property for the default value type if no header.
     */
    public static final String VALUE_DEFAULT_TYPE = "spring.json.value.default.type";

    /**
     * Kafka config property for trusted deserialization packages.
     */
    public static final String TRUSTED_PACKAGES = "spring.json.trusted.packages";

    protected final ObjectMapper objectMapper = RpcSerializer.getObjectMapper();

    protected Class<T> targetType;

    private volatile ObjectReader reader;

    protected Jackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();

    private boolean typeMapperExplicitlySet = false;

    public Jackson2JavaTypeMapper getTypeMapper() {
        return this.typeMapper;
    }

    /**
     * Set a customized type mapper.
     *
     * @param typeMapper the type mapper.
     * @since 2.1
     */
    public void setTypeMapper(Jackson2JavaTypeMapper typeMapper) {
        Assert.notNull(typeMapper, "'typeMapper' cannot be null");
        this.typeMapper = typeMapper;
        this.typeMapperExplicitlySet = true;
    }

    /**
     * Configure the default Jackson2JavaTypeMapper to use key type headers.
     *
     * @param isKey Use key type headers if true
     * @since 2.1.3
     */
    public void setUseTypeMapperForKey(boolean isKey) {
        if (!this.typeMapperExplicitlySet) {
            if (this.getTypeMapper() instanceof AbstractJavaTypeMapper) {
                AbstractJavaTypeMapper typeMapper = (AbstractJavaTypeMapper) this.getTypeMapper();
                typeMapper.setUseForKey(isKey);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        setUseTypeMapperForKey(isKey);
        try {
            if (isKey && configs.containsKey(KEY_DEFAULT_TYPE)) {
                if (configs.get(KEY_DEFAULT_TYPE) instanceof Class) {
                    this.targetType = (Class<T>) configs.get(KEY_DEFAULT_TYPE);
                } else if (configs.get(KEY_DEFAULT_TYPE) instanceof String) {
                    this.targetType = (Class<T>) ClassUtils.forName((String) configs.get(KEY_DEFAULT_TYPE), null);
                } else {
                    throw new IllegalStateException(KEY_DEFAULT_TYPE + " must be Class or String");
                }
            }
            // TODO don't forget to remove these code after DEFAULT_VALUE_TYPE being removed.
            else if (!isKey && configs.containsKey("spring.json.default.value.type")) {
                if (configs.get("spring.json.default.value.type") instanceof Class) {
                    this.targetType = (Class<T>) configs.get("spring.json.default.value.type");
                } else if (configs.get("spring.json.default.value.type") instanceof String) {
                    this.targetType = (Class<T>) ClassUtils
                            .forName((String) configs.get("spring.json.default.value.type"), null);
                } else {
                    throw new IllegalStateException("spring.json.default.value.type must be Class or String");
                }
            } else if (!isKey && configs.containsKey(VALUE_DEFAULT_TYPE)) {
                if (configs.get(VALUE_DEFAULT_TYPE) instanceof Class) {
                    this.targetType = (Class<T>) configs.get(VALUE_DEFAULT_TYPE);
                } else if (configs.get(VALUE_DEFAULT_TYPE) instanceof String) {
                    this.targetType = (Class<T>) ClassUtils.forName((String) configs.get(VALUE_DEFAULT_TYPE), null);
                } else {
                    throw new IllegalStateException(VALUE_DEFAULT_TYPE + " must be Class or String");
                }
            }
            addTargetPackageToTrusted();
        } catch (ClassNotFoundException | LinkageError e) {
            throw new IllegalStateException(e);
        }
        if (configs.containsKey(TRUSTED_PACKAGES)) {
            if (configs.get(TRUSTED_PACKAGES) instanceof String) {
                this.typeMapper.addTrustedPackages(
                        StringUtils.commaDelimitedListToStringArray((String) configs.get(TRUSTED_PACKAGES)));
            }
        }
    }

    /**
     * Add trusted packages for deserialization.
     *
     * @param packages the packages.
     * @since 2.1
     */
    public void addTrustedPackages(String... packages) {
        this.typeMapper.addTrustedPackages(packages);
    }

    private void addTargetPackageToTrusted() {
        if (this.targetType != null) {
            addTrustedPackages(this.targetType.getPackage().getName());
        }
    }

    @Override
    public T deserialize(String topic, Headers headers, byte[] data) {
        if (data == null) {
            return null;
        }
        try {
            JavaType javaType = this.typeMapper.toJavaType(headers);

            if (javaType == null) {
                Assert.state(this.targetType != null, "No type information in headers and no default type provided");
                return deserialize(topic, data);
            } else {
                try {
                    return this.objectMapper.readerFor(javaType).readValue(data);
                } catch (IOException e) {
                    logger.error("Can't deserialize data [" + Arrays.toString(data) +
                            "] from topic [" + topic + "]", e);
                    return null;
                }
            }
        } catch (Exception e) {
            logger.error("Can't deserialize data [" + Arrays.toString(data) +
                    "] from topic [" + topic + "]", e);
            return null;
        }
    }

    @Override
    public T deserialize(String topic, byte[] data) {
        if (data == null) {
            return null;
        }
        if (this.reader == null) {
            this.reader = this.objectMapper.readerFor(this.targetType);
        }
        try {
            T result = null;
            if (data != null) {
                result = this.reader.readValue(data);
            }
            return result;
        } catch (IOException e) {
            logger.error("Can't deserialize data [" + Arrays.toString(data) +
                    "] from topic [" + topic + "]", e);
            return null;
        }
    }

    @Override
    public void close() {
        // No-op
    }

}
