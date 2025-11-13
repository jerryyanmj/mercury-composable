package com.example.graphql.model;

import java.util.Map;

public class QueryDefinition {
    private String name;
    private String queryString;
    private Map<String, Object> variableSchema;
    private Map<String, String> dataSources;
    private long createdAt;
    private String returnSchema;

    public QueryDefinition() {
        this.createdAt = System.currentTimeMillis();
        this.variableSchema = Map.of("id", Map.of("type", "string", "required", true));
    }

    public QueryDefinition(String name, String queryString, String returnSchema, Map<String, String> dataSources) {
        this();
        this.name = name;
        this.queryString = queryString;
        this.returnSchema = returnSchema;
        this.dataSources = dataSources != null ? dataSources : Map.of();
    }

    public QueryDefinition(String name, String queryString, String returnSchema) {
        this(name, queryString, returnSchema, Map.of());
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getQueryString() {
        return queryString;
    }

    public void setQueryString(String queryString) {
        this.queryString = queryString;
    }

    public Map<String, Object> getVariableSchema() {
        return variableSchema;
    }

    public void setVariableSchema(Map<String, Object> variableSchema) {
        this.variableSchema = variableSchema != null ? variableSchema : Map.of();
    }

    public Map<String, String> getDataSources() {
        return dataSources != null ? dataSources : Map.of();
    }

    public void setDataSources(Map<String, String> dataSources) {
        this.dataSources = dataSources != null ? dataSources : Map.of();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public String getReturnSchema() {
        return returnSchema;
    }

    public void setReturnSchema(String returnSchema) {
        this.returnSchema = returnSchema;
    }

    public boolean hasDataSources() {
        return dataSources != null && !dataSources.isEmpty();
    }

    public String getDataSource(String key) {
        return dataSources != null ? dataSources.get(key) : null;
    }

    @Override
    public String toString() {
        return "QueryDefinition{" +
                "name='" + name + '\'' +
                ", returnSchema='" + returnSchema + '\'' +
                ", queryString='" + queryString + '\'' +
                ", dataSources=" + dataSources +
                ", variableSchema=" + variableSchema +
                ", createdAt=" + createdAt +
                '}';
    }
}