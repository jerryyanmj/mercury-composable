package com.example.graphql.model;

import java.util.Map;

public class QueryDefinition {
    private String name;
    private String queryString;
    private Map<String, Object> variableSchema;
    private Map<String, String> dataSources;
    private long createdAt;

    // 默认构造函数（用于 JSON 反序列化）
    public QueryDefinition() {
        this.createdAt = System.currentTimeMillis();
        this.variableSchema = Map.of("id", Map.of("type", "string", "required", true));
    }

    // 全参数构造函数
    public QueryDefinition(String name, String queryString, Map<String, String> dataSources) {
        this();
        this.name = name;
        this.queryString = queryString;
        this.dataSources = dataSources != null ? dataSources : Map.of();
    }

    // 简化构造函数（向后兼容）
    public QueryDefinition(String name, String queryString) {
        this(name, queryString, Map.of());
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

    // 新增：数据源映射的 getter 和 setter
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

    // 辅助方法：检查是否有数据源配置
    public boolean hasDataSources() {
        return dataSources != null && !dataSources.isEmpty();
    }

    // 辅助方法：获取特定数据源
    public String getDataSource(String key) {
        return dataSources != null ? dataSources.get(key) : null;
    }

    @Override
    public String toString() {
        return "QueryDefinition{" +
                "name='" + name + '\'' +
                ", queryString='" + queryString + '\'' +
                ", dataSources=" + dataSources +
                ", variableSchema=" + variableSchema +
                ", createdAt=" + createdAt +
                '}';
    }
}