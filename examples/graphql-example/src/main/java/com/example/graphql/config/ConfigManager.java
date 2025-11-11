package com.example.graphql.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConfigManager {
    private final Map<String, DataSourceConfig> dataSources = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void loadDataSources(String configPath) {
        try (InputStream is = getClass().getResourceAsStream(configPath)) {
            if (is == null) {
                throw new RuntimeException("Config file not found: " + configPath);
            }

            DataSourceConfig[] configs = objectMapper.readValue(is, DataSourceConfig[].class);
            for (DataSourceConfig config : configs) {
                dataSources.put(config.getName(), config);
                System.out.println("Loaded data source: " + config.getName());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load data source config: " + configPath, e);
        }
    }

    public DataSourceConfig getDataSource(String name) {
        return dataSources.get(name);
    }

    public Map<String, DataSourceConfig> getAllDataSources() {
        return new ConcurrentHashMap<>(dataSources);
    }
}