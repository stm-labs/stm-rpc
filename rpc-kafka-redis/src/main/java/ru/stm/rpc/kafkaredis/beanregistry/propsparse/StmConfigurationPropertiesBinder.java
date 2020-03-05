package ru.stm.rpc.kafkaredis.beanregistry.propsparse;

import org.springframework.beans.PropertyEditorRegistry;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.bind.handler.IgnoreErrorsBindHandler;
import org.springframework.boot.context.properties.bind.handler.IgnoreTopLevelConverterNotFoundBindHandler;
import org.springframework.boot.context.properties.bind.handler.NoUnboundElementsBindHandler;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.UnboundElementsSourceFilter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySources;
import org.springframework.util.Assert;

import java.util.function.Consumer;

public class StmConfigurationPropertiesBinder {

    private final ApplicationContext applicationContext;

    private final PropertySources propertySources;

    private volatile Binder binder;

    StmConfigurationPropertiesBinder(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        this.propertySources = new StmCompositePropertySources(((ConfigurableEnvironment) this.applicationContext.getEnvironment()).getPropertySources());
    }

    public void bind(Bindable<?> target) {
        ConfigurationProperties annotation = target
                .getAnnotation(ConfigurationProperties.class);
        Assert.state(annotation != null,
                () -> "Missing @ConfigurationProperties on " + target);
        BindHandler bindHandler = getBindHandler(annotation);
        getBinder().bind(annotation.prefix(), target, bindHandler);
    }

    private BindHandler getBindHandler(ConfigurationProperties annotation) {
        BindHandler handler = new IgnoreTopLevelConverterNotFoundBindHandler();
        if (annotation.ignoreInvalidFields()) {
            handler = new IgnoreErrorsBindHandler(handler);
        }
        if (!annotation.ignoreUnknownFields()) {
            UnboundElementsSourceFilter filter = new UnboundElementsSourceFilter();
            handler = new NoUnboundElementsBindHandler(handler, filter);
        }
        return handler;
    }

    private Binder getBinder() {
        if (this.binder == null) {
            this.binder = new Binder(getConfigurationPropertySources(),
                    getPropertySourcesPlaceholdersResolver(), getConversionService(),
                    getPropertyEditorInitializer());
        }
        return this.binder;
    }

    private Iterable<ConfigurationPropertySource> getConfigurationPropertySources() {
        return ConfigurationPropertySources.from(((ConfigurableEnvironment) this.applicationContext.getEnvironment()).getPropertySources());
    }

    private PropertySourcesPlaceholdersResolver getPropertySourcesPlaceholdersResolver() {
        return new PropertySourcesPlaceholdersResolver(this.propertySources);
    }

    private ConversionService getConversionService() {
        return new StmConversionServiceDeducer(this.applicationContext)
                .getConversionService();
    }

    private Consumer<PropertyEditorRegistry> getPropertyEditorInitializer() {
        if (this.applicationContext instanceof ConfigurableApplicationContext) {
            return ((ConfigurableApplicationContext) this.applicationContext)
                    .getBeanFactory()::copyRegisteredEditorsTo;
        }
        return null;
    }

}
