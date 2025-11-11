package com.example.graphql.datasource;

import com.example.graphql.config.DataSourceConfig;
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
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ConfigurableDataFetcher implements DataFetcher<CompletableFuture<Map<String, Object>>> {
    private final QueryDefinition query;
    private final DataSourceConfig dataSource;
    private final String endpointName;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ConfigurableDataFetcher(QueryDefinition query, DataSourceConfig dataSource, String endpointName) {
        this.query = query;
        this.dataSource = dataSource;
        this.endpointName = endpointName;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public CompletableFuture<Map<String, Object>> get(DataFetchingEnvironment environment) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                EndpointConfig endpoint = dataSource.getEndpoint(endpointName);
                if (endpoint == null) {
                    throw new RuntimeException("Endpoint not found: " + endpointName);
                }

                // 构建请求 URL（替换路径参数）
                String path = buildPath(endpoint.getPath(), environment);
                String url = dataSource.getBaseUrl() + path;

                // 创建 HTTP 请求
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json");

                // 添加自定义 headers
                endpoint.getHeaders().forEach(requestBuilder::header);

                HttpRequest request = requestBuilder.build();

                System.out.println("Making HTTP request: " + request.method() + " " + url);

                // 发送请求
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    throw new RuntimeException("HTTP error: " + response.statusCode() + " - " + response.body());
                }

                // 解析响应并映射字段
                return mapResponse(response.body(), endpoint.getResponseMapping());

            } catch (Exception e) {
                throw new RuntimeException("Failed to fetch data from data source: " + dataSource.getName(), e);
            }
        });
    }

    private String buildPath(String path, DataFetchingEnvironment environment) {
        // 替换路径中的 {id} 等参数
        String result = path;
        if (result.contains("{id}")) {
            String id = environment.getArgument("id");
            result = result.replace("{id}", id != null ? id : "");
        }
        return result;
    }

    private Map<String, Object> mapResponse(String responseBody, Map<String, String> fieldMappings) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        Map<String, Object> result = new HashMap<>();

        for (Map.Entry<String, String> mapping : fieldMappings.entrySet()) {
            String graphQLField = mapping.getKey();
            String jsonPath = mapping.getValue();

            // 简单的 JSON 路径解析（支持 $.field 格式）
            JsonNode value = root.at(jsonPath.startsWith("$.") ? jsonPath : "$." + jsonPath);
            if (!value.isMissingNode()) {
                if (value.isTextual()) {
                    result.put(graphQLField, value.asText());
                } else if (value.isNumber()) {
                    result.put(graphQLField, value.asInt());
                } else if (value.isBoolean()) {
                    result.put(graphQLField, value.asBoolean());
                } else {
                    result.put(graphQLField, value);
                }
            }
        }

        return result;
    }
}