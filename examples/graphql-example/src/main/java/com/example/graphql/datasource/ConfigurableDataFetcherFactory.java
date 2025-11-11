package com.example.graphql.datasource;

import com.example.graphql.config.DataSourceConfigManager;
import com.example.graphql.model.QueryDefinition;
import graphql.schema.DataFetcher;

public class ConfigurableDataFetcherFactory implements DataFetcherFactory {
    private final DataSourceConfigManager configManager;

    public ConfigurableDataFetcherFactory(DataSourceConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public DataFetcher<?> createDataFetcher(QueryDefinition query) {
        return new ConfigurableDataFetcher(query, configManager);
    }
}