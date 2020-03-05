package ru.stm.rpc.kafkaredis.beanregistry.propsparse;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.GenericConverter;

import java.util.Collections;
import java.util.List;

public class StmConversionServiceDeducer {

    private final ApplicationContext applicationContext;

    StmConversionServiceDeducer(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public ConversionService getConversionService() {
        try {
            return this.applicationContext.getBean(
                    ConfigurableApplicationContext.CONVERSION_SERVICE_BEAN_NAME,
                    ConversionService.class);
        }
        catch (NoSuchBeanDefinitionException ex) {
            return this.applicationContext.getAutowireCapableBeanFactory()
                    .createBean(StmConversionServiceDeducer.Factory.class).create();
        }
    }

    private static class Factory {

        private List<Converter<?, ?>> converters = Collections.emptyList();

        private List<GenericConverter> genericConverters = Collections.emptyList();

        /**
         * A list of custom converters (in addition to the defaults) to use when
         * converting properties for binding.
         * @param converters the converters to set
         */
        @Autowired(required = false)
        @ConfigurationPropertiesBinding
        public void setConverters(List<Converter<?, ?>> converters) {
            this.converters = converters;
        }

        /**
         * A list of custom converters (in addition to the defaults) to use when
         * converting properties for binding.
         * @param converters the converters to set
         */
        @Autowired(required = false)
        @ConfigurationPropertiesBinding
        public void setGenericConverters(List<GenericConverter> converters) {
            this.genericConverters = converters;
        }

        public ConversionService create() {
            if (this.converters.isEmpty() && this.genericConverters.isEmpty()) {
                return ApplicationConversionService.getSharedInstance();
            }
            ApplicationConversionService conversionService = new ApplicationConversionService();
            for (Converter<?, ?> converter : this.converters) {
                conversionService.addConverter(converter);
            }
            for (GenericConverter genericConverter : this.genericConverters) {
                conversionService.addConverter(genericConverter);
            }
            return conversionService;
        }

    }

}
