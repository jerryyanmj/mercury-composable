// com/example/graphql/config/DataSourceConfig.java
package com.example.graphql.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class DataSourceConfig {
    private final String name;
    private final String type;
    private final String baseUrl;
    private final Map<String, EndpointConfig> endpoints;

    @JsonCreator
    public DataSourceConfig(
            @JsonProperty("name") String name,
            @JsonProperty("type") String type,
            @JsonProperty("baseUrl") String baseUrl,
            @JsonProperty("endpoints") Map<String, EndpointConfig> endpoints) {
        this.name = name;
        this.type = type;
        this.baseUrl = baseUrl;
        this.endpoints = endpoints;
    }

    // Getters
    public String getName() { return name; }
    public String getType() { return type; }
    public String getBaseUrl() { return baseUrl; }
    public Map<String, EndpointConfig> getEndpoints() { return endpoints; }
    public EndpointConfig getEndpoint(String name) {
        return endpoints != null ? endpoints.get(name) : null;
    }
}