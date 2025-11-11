package com.example.graphql.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class EndpointConfig {
    private final String path;
    private final String method;
    private final Map<String, String> headers;
    private final Map<String, String> responseMapping;

    @JsonCreator
    public EndpointConfig(
            @JsonProperty("path") String path,
            @JsonProperty("method") String method,
            @JsonProperty("headers") Map<String, String> headers,
            @JsonProperty("responseMapping") Map<String, String> responseMapping) {
        this.path = path;
        this.method = method;
        this.headers = headers != null ? headers : Map.of();
        this.responseMapping = responseMapping != null ? responseMapping : Map.of();
    }

    // Getters
    public String getPath() { return path; }
    public String getMethod() { return method; }
    public Map<String, String> getHeaders() { return headers; }
    public Map<String, String> getResponseMapping() { return responseMapping; }
}