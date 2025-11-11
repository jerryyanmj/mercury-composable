package com.example.graphql.model;

import com.example.graphql.datasource.DataSourceType;

import java.util.Map;

public class QueryDefinition {
    private String name;
    private String queryString;
    private Map<String, Object> variableSchema;
    private DataSourceType dataSourceType;
    private long createdAt;

    public QueryDefinition() {
        this.createdAt = System.currentTimeMillis();
    }

    public QueryDefinition(String name, String queryString, Map<String, Object> variableSchema) {
        this();
        this.name = name;
        this.queryString = queryString;
        this.variableSchema = variableSchema;
    }

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getQueryString() { return queryString; }
    public void setQueryString(String queryString) { this.queryString = queryString; }

    public Map<String, Object> getVariableSchema() { return variableSchema; }
    public void setVariableSchema(Map<String, Object> variableSchema) { this.variableSchema = variableSchema; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public DataSourceType getDataSourceType() {
        return dataSourceType;
    }

    public void setDataSourceType(DataSourceType dataSourceType) {
        this.dataSourceType = dataSourceType;
    }
}