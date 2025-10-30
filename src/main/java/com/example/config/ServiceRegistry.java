package com.example.config;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class ServiceRegistry {

    private final Map<String, String> services = new HashMap<>();

    public void registerService(String serviceName, String serviceUrl) {
        services.put(serviceName, serviceUrl);
    }

    public String getServiceUrl(String serviceName) {
        return services.get(serviceName);
    }
}
