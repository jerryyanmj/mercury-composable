package com.example.graphql.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DataSourceConfigManager {
    private final Map<String, DataSourceConfig> dataSources = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void loadDataSources(String configPath) {
        try (InputStream is = getClass().getResourceAsStream(configPath)) {
            if (is == null) {
                System.out.println("Data source config file not found: " + configPath + ", using default behavior");
                return;
            }

            // 读取配置文件
            Map<String, DataSourceConfig> configs = objectMapper.readValue(is,
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, DataSourceConfig.class));

            dataSources.putAll(configs);
            System.out.println("Loaded " + dataSources.size() + " data sources: " + dataSources.keySet());

        } catch (Exception e) {
            System.err.println("Failed to load data source config: " + configPath + ", error: " + e.getMessage());
            // 不抛出异常，保持向后兼容
        }
    }

    public DataSourceConfig getDataSource(String name) {
        return dataSources.get(name);
    }

    public Map<String, DataSourceConfig> getAllDataSources() {
        return new ConcurrentHashMap<>(dataSources);
    }

    public boolean hasDataSources() {
        return !dataSources.isEmpty();
    }
}