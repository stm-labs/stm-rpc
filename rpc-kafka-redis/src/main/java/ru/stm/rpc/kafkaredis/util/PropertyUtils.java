package ru.stm.rpc.kafkaredis.util;

import org.springframework.core.env.*;

import java.util.HashMap;
import java.util.Map;

public class PropertyUtils {

    public static Map<String, Object> getAllKnownProperties(Environment env) {
        Map<String, Object> rtn = new HashMap<>();
        if (env instanceof ConfigurableEnvironment) {
            for (PropertySource<?> propertySource : ((ConfigurableEnvironment) env).getPropertySources()) {
                if (propertySource instanceof EnumerablePropertySource) {
                    for (String key : ((EnumerablePropertySource) propertySource).getPropertyNames()) {
                        rtn.put(key, propertySource.getProperty(key));
                    }
                }

                if (propertySource instanceof SystemEnvironmentPropertySource) {
                    for (String key : ((EnumerablePropertySource) propertySource).getPropertyNames()) {
                        rtn.put(key, propertySource.getProperty(key));
                    }
                }

            }
        }
        return rtn;
    }

}
