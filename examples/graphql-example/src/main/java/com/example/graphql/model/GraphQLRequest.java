// com/example/graphql/model/GraphQLRequest.java
package com.example.graphql.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class GraphQLRequest {
    private final String queryName;
    private final Map<String, Object> variables;

    @JsonCreator
    public GraphQLRequest(
            @JsonProperty("queryName") String queryName,
            @JsonProperty("variables") Map<String, Object> variables) {
        this.queryName = queryName;
        this.variables = variables != null ? variables : Map.of();
    }

    public String getQueryName() { return queryName; }
    public Map<String, Object> getVariables() { return variables; }
}