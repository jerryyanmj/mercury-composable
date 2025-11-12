package com.example.graphql.datasource;

import com.example.graphql.config.DataSourceConfig;
import com.example.graphql.config.DataSourceConfigManager;
import com.example.graphql.config.EndpointConfig;
import com.example.graphql.model.QueryDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ConfigurableDataFetcher implements DataFetcher<Object> {
    private final QueryDefinition query;
    private final DataSourceConfigManager configManager;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ConfigurableDataFetcher(QueryDefinition query, DataSourceConfigManager configManager) {
        this.query = query;
        this.configManager = configManager;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Object get(DataFetchingEnvironment environment) {
        // 如果查询没有配置数据源，回退到默认行为
        if (!query.hasDataSources()) {
            System.out.println("Query " + query.getName() + " has no data source configuration, using default behavior");
            return getDefaultData(environment);
        }

        System.out.println("=== ConfigurableDataFetcher ===");
        System.out.println("Query: " + query.getName());
        System.out.println("DataSources: " + query.getDataSources());

        try {
            Map<String, Object> result = new HashMap<>();

            // 为每个配置的数据源获取数据
            for (Map.Entry<String, String> dataSourceEntry : query.getDataSources().entrySet()) {
                String sourceKey = dataSourceEntry.getKey();
                String dataSourceRef = dataSourceEntry.getValue();

                System.out.println("Processing data source: " + sourceKey + " -> " + dataSourceRef);

                // 解析数据源引用（格式：serviceName.endpointName）
                String[] parts = dataSourceRef.split("\\.");
                if (parts.length != 2) {
                    System.err.println("Invalid data source reference: " + dataSourceRef);
                    continue;
                }

                String serviceName = parts[0];
                String endpointName = parts[1];

                DataSourceConfig dataSource = configManager.getDataSource(serviceName);
                if (dataSource == null) {
                    System.err.println("Data source not found: " + serviceName +  ", available: " + configManager.getAllDataSources().keySet());
                    continue;
                }

                System.out.println("Found data source: " + dataSource.getName() + ", baseUrl: " + dataSource.getBaseUrl());

                EndpointConfig endpoint = dataSource.getEndpoint(endpointName);
                if (endpoint == null) {
                    System.err.println("Endpoint not found: " + endpointName + " in service " + serviceName + ", available: " + dataSource.getEndpoints().keySet());
                    continue;
                }

                System.out.println("Found endpoint: " + endpointName + ", path: " + endpoint.getPath());

                var responseData = fetchFromDataSource(dataSource, endpoint, environment);

                if (responseData instanceof Map) {
                    System.out.println("Raw response keys: " + ((Map<?, ?>) responseData).keySet());
                    result.putAll((Map<String, Object>) responseData);
                } else if (responseData instanceof List) {
                    result.put(sourceKey, responseData);
                }
            }

            System.out.println("Final combined result: " + result.keySet());

            if (result.size() == 1 && result.get(result.keySet().toArray(new String[0])[0]) instanceof List<?>) {
                return result.get(result.keySet().toArray(new String[0])[0]);
            }

            return result;

        } catch (Exception e) {
            System.err.println("Error in ConfigurableDataFetcher for query " + query.getName() + ": " + e.getMessage());
            return getDefaultData(environment);
        }
    }

    private Object fetchFromDataSource(DataSourceConfig dataSource, EndpointConfig endpoint, DataFetchingEnvironment environment) {
        try {
            String id = environment.getArgument("id");

            String path = endpoint.getPath().replace("{id}", id).replace("{userId}", id);
            String url = dataSource.getBaseUrl() + path;

            System.out.println("Mock calling: " + endpoint.getMethod() + " " + url);

            Map<String, String> headers = endpoint.getHeaders() != null ?
                    endpoint.getHeaders() : Map.of("Accept", "application/json");

            return HttpFetcher.fetchHttp(
                    url,
                    endpoint.getMethod(),
                    headers
            );
        } catch (Exception e) {
            System.err.println("Failed to fetch from data source: " + dataSource.getName() + ", error: " + e.getMessage());
            return Map.of();
        }
    }

    private Map<String, Object> getMockDataForEndpoint(String serviceName, String endpointPath, String id) {
        System.out.println("Getting mock data for: " + serviceName + " - " + endpointPath + " - id: " + id);

        // make sure serviceName is not null
        if (serviceName == null) {
            System.err.println("ERROR: serviceName is null in getMockDataForEndpoint!");
            return Map.of("id", "error_" + id, "name", "Error: serviceName is null");
        }

        if ("userService".equals(serviceName) && endpointPath.contains("/api/user/")) {
            return Map.of(
                    "id", id,
                    "name", "Config User " + id,
                    "email", "config.user" + id + "@example.com",
                    "fullName", "Config User " + id + " Full Name",
                    "hobbies", java.util.List.of("reading", "config"),
                    "phone", "555-0100",
                    "address", "Config Address"
            );
        } else if ("postService".equals(serviceName) && endpointPath.contains("/api/post/")) {
            return Map.of(
                    "id", id,
                    "title", "Config Post " + id,
                    "content", "This is config post content for " + id,
                    "createdAt", "2024-01-01",
                    "updatedAt", "2024-01-02",
                    "author", "config_author_" + id,
                    "tags", java.util.List.of("config", "graphql")
            );
        } else if ("postService".equals(serviceName) && endpointPath.contains("/api/posts?")) {
            return Map.of(
                    "posts", java.util.List.of(
                            Map.of("id", "config_post_1_" + id, "title", "Config Post 1 for " + id),
                            Map.of("id", "config_post_2_" + id, "title", "Config Post 2 for " + id)
                    )
            );
        } else {
            System.err.println("No mock data mapping for: " + serviceName + " - " + endpointPath);
            return Map.of("id", "fallback_" + id, "name", "Fallback Data");
        }
    }

    private Object getDefaultData(DataFetchingEnvironment environment) {
        // Fall back to default data
        String id = environment.getArgument("id");
        if (query.getName().contains("WithPosts")) {
            return new SimpleDataFetcherFactory().getUserWithPostsData(id);
        } else if (query.getName().contains("Post")) {
            return new SimpleDataFetcherFactory().getPostData(id);
        } else {
            return new SimpleDataFetcherFactory().getUserData(id);
        }
    }
}