package com.example.graphql.datasource;

import com.example.graphql.config.ConfigManager;
import com.example.graphql.config.DataSourceConfig;
import com.example.graphql.model.QueryDefinition;
import graphql.schema.DataFetcher;

public class ConfigurableDataFetcherFactory implements DataFetcherFactory {
    private final ConfigManager configManager;

    public ConfigurableDataFetcherFactory(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public DataFetcher<?> createDataFetcher(QueryDefinition query) {
        // 从查询配置中提取数据源信息（稍后我们会扩展 QueryDefinition）
        String dataSourceRef = extractDataSourceRef(query);

        if (dataSourceRef != null) {
            String[] parts = dataSourceRef.split("\\.");
            if (parts.length == 2) {
                String dataSourceName = parts[0];
                String endpointName = parts[1];

                DataSourceConfig dataSource = configManager.getDataSource(dataSourceName);
                if (dataSource != null) {
                    return new ConfigurableDataFetcher(query, dataSource, endpointName);
                }
            }
        }

        // 回退到简单的 DataFetcher
        return new SimpleDataFetcherFactory().createDataFetcher(query);
    }

    private String extractDataSourceRef(QueryDefinition query) {
        // 暂时从查询名称推断，稍后我们会扩展 QueryDefinition 来包含数据源配置
        if (query.getName().contains("User")) {
            return "userService.getUser";
        } else if (query.getName().contains("Post")) {
            return "postService.getPost";
        }
        return null;
    }
}