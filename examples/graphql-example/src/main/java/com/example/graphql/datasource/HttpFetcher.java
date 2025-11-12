package com.example.graphql.datasource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class HttpFetcher {
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static Object fetchHttp(String url, String method, Map<String, String> headers) {
        try {
            // 构建请求
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30));

            // 设置方法
            switch (method.toUpperCase()) {
                case "GET":
                    requestBuilder.GET();
                    break;
                case "POST":
                    requestBuilder.POST(HttpRequest.BodyPublishers.noBody());
                    break;
                default:
                    requestBuilder.GET();
            }

            // 设置 headers
            if (headers != null) {
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    requestBuilder.header(header.getKey(), header.getValue());
                }
            }

            HttpRequest request = requestBuilder.build();

            // 发送请求
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            // 检查响应状态
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String responseBody = response.body().trim();

                // 根据响应内容判断是对象还是数组
                if (responseBody.startsWith("[")) {
                    // 解析为数组/列表
                    return objectMapper.readValue(responseBody, new TypeReference<List<Object>>() {});
                } else if (responseBody.startsWith("{")) {
                    // 解析为对象/Map
                    return objectMapper.readValue(responseBody, new TypeReference<Map<String, Object>>() {});
                } else {
                    // 其他类型，返回原始字符串
                    return responseBody;
                }
            } else {
                System.err.println("HTTP request failed: " + response.statusCode() + " - " + response.body());
                return Map.of("error", "HTTP " + response.statusCode());
            }

        } catch (Exception e) {
            System.err.println("Failed to fetch from URL: " + url + ", error: " + e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }
}