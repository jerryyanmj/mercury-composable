package com.example.graphql.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.List;
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

            System.out.println("Loading data sources from: " + configPath);

            List<DataSourceConfig> configList = objectMapper.readValue(is,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, DataSourceConfig.class));

            for (DataSourceConfig config : configList) {
                if (config.getName() != null) {
                    dataSources.put(config.getName(), config);
                    System.out.println("✓ Loaded data source: " + config.getName() +
                            " (baseUrl: " + config.getBaseUrl() +
                            ", endpoints: " + config.getEndpoints().keySet() + ")");
                } else {
                    System.err.println("✗ Data source config has null name: " + config);
                }
            }

            System.out.println("Successfully loaded " + dataSources.size() + " data sources");

        } catch (Exception e) {
            System.err.println("Failed to load data source config: " + configPath + ", error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public DataSourceConfig getDataSource(String name) {
        DataSourceConfig config = dataSources.get(name);
        if (config == null) {
            System.err.println("DataSourceConfigManager: Data source '" + name + "' not found!");
            System.err.println("Available data sources: " + dataSources.keySet());
        }
        return config;
    }

    public Map<String, DataSourceConfig> getAllDataSources() {
        return new ConcurrentHashMap<>(dataSources);
    }

    public boolean hasDataSources() {
        return !dataSources.isEmpty();
    }
}