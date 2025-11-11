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

                System.out.println("Response received: " + response.body());

                // 解析响应并映射字段
                return mapResponse(response.body(), endpoint.getResponseMapping());

            } catch (Exception e) {
                System.err.println("Error fetching data: " + e.getMessage());
                e.printStackTrace();
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

        System.out.println("Mapping response with mappings: " + fieldMappings);

        for (Map.Entry<String, String> mapping : fieldMappings.entrySet()) {
            String graphQLField = mapping.getKey();
            String jsonPath = mapping.getValue();

            // 转换 JSONPath ($.field) 到 JSON Pointer (/field)
            String jsonPointer = convertJsonPathToPointer(jsonPath);

            System.out.println("Mapping field: " + graphQLField + " from path: " + jsonPath + " -> " + jsonPointer);

            JsonNode value = root.at(jsonPointer);
            if (!value.isMissingNode()) {
                Object fieldValue = extractValue(value);
                result.put(graphQLField, fieldValue);
                System.out.println("Mapped " + graphQLField + " = " + fieldValue);
            } else {
                System.out.println("Field not found: " + jsonPointer);
            }
        }

        System.out.println("Final mapped result: " + result);
        return result;
    }

    private String convertJsonPathToPointer(String jsonPath) {
        // 转换 $.field.subfield 到 /field/subfield
        // 转换 $.field[0] 到 /field/0
        if (jsonPath.startsWith("$.")) {
            String path = jsonPath.substring(2); // 移除 "$."
            // 将点分隔符转换为斜杠
            path = path.replace('.', '/');
            // 确保以斜杠开头
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            return path;
        } else if (jsonPath.startsWith("$[")) {
            // 处理数组情况
            return jsonPath.substring(1); // 移除 "$" -> "/[0]" 等
        } else {
            // 默认情况，直接使用
            return jsonPath.startsWith("/") ? jsonPath : "/" + jsonPath;
        }
    }

    private Object extractValue(JsonNode node) {
        if (node.isTextual()) {
            return node.asText();
        } else if (node.isNumber()) {
            if (node.isInt()) {
                return node.asInt();
            } else if (node.isLong()) {
                return node.asLong();
            } else {
                return node.asDouble();
            }
        } else if (node.isBoolean()) {
            return node.asBoolean();
        } else if (node.isArray()) {
            // 处理数组
            return objectMapper.convertValue(node, Object.class);
        } else if (node.isObject()) {
            // 处理对象
            return objectMapper.convertValue(node, Object.class);
        } else if (node.isNull()) {
            return null;
        } else {
            return node.toString();
        }
    }
}