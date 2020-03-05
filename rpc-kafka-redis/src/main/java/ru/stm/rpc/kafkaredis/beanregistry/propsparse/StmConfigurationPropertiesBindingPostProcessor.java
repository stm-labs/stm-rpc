package ru.stm.rpc.kafkaredis.beanregistry.propsparse;

import org.springframework.beans.BeansException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.validation.annotation.Validated;
import ru.stm.platform.StmExecutionError;

import java.lang.annotation.Annotation;

public class StmConfigurationPropertiesBindingPostProcessor {

    private final ApplicationContext applicationContext;

    private StmConfigurationPropertiesBinder configurationPropertiesBinder;

    public StmConfigurationPropertiesBindingPostProcessor(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        this.configurationPropertiesBinder = new StmConfigurationPropertiesBinder(this.applicationContext);
    }

    public Object postProcessBeforeInitialization(Object bean) throws BeansException {
        ConfigurationProperties annotation = getAnnotation(bean, ConfigurationProperties.class);
        if (annotation != null) {
            bind(bean, annotation);
        }
        return bean;
    }

    private void bind(Object bean, ConfigurationProperties annotation) {
        ResolvableType type = getBeanType(bean);
        Validated validated = getAnnotation(bean, Validated.class);
        Annotation[] annotations = (validated != null)
                ? new Annotation[]{annotation, validated}
                : new Annotation[]{annotation};
        Bindable<?> target = Bindable.of(type).withExistingValue(bean)
                .withAnnotations(annotations);
        try {
            this.configurationPropertiesBinder.bind(target);
        } catch (Exception ex) {
            throw new StmExecutionError("rpc init error", ex);
        }
    }

    private ResolvableType getBeanType(Object bean) {
        return ResolvableType.forClass(bean.getClass());
    }

    private <A extends Annotation> A getAnnotation(Object bean, Class<A> type) {
        return AnnotationUtils.findAnnotation(bean.getClass(), type);
    }

}
