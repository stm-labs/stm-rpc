package ru.stm.rpc.router.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Configuration for RPC router
 */
@Data
@ConfigurationProperties("stm.rpc.router")
@Component
public class RpcRouterProperties {

    private Map<String, RpcRouterRoute> route;

    /**
     * Default router settings
     */
    private RpcRouterRouteDefault def;

    @Data
    public static class RpcRouterRoute {
        private String destination;

        private String namespace;

    }

    @Data
    public static class RpcRouterRouteDefault {
        private String destination;
        private String topic;
        private String namespace;
    }

}
