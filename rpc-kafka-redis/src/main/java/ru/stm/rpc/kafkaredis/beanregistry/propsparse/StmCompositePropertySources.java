package ru.stm.rpc.kafkaredis.beanregistry.propsparse;

import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySources;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.StreamSupport;

public class StmCompositePropertySources implements PropertySources {

    private final List<PropertySources> propertySources;

    StmCompositePropertySources(PropertySources... propertySources) {
        this.propertySources = Arrays.asList(propertySources);
    }

    @Override
    public Iterator<PropertySource<?>> iterator() {
        return this.propertySources.stream()
                .flatMap((sources) -> StreamSupport.stream(sources.spliterator(), false))
                .iterator();
    }

    @Override
    public boolean contains(String name) {
        for (PropertySources sources : this.propertySources) {
            if (sources.contains(name)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public PropertySource<?> get(String name) {
        for (PropertySources sources : this.propertySources) {
            PropertySource<?> source = sources.get(name);
            if (source != null) {
                return source;
            }
        }
        return null;
    }
}
