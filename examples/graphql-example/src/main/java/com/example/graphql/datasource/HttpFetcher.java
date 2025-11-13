package com.example.graphql.datasource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class HttpFetcher {
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static Object fetchHttp(String url, String method, Map<String, String> headers) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30));

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

            if (headers != null) {
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    requestBuilder.header(header.getKey(), header.getValue());
                }
            }

            HttpRequest request = requestBuilder.build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String responseBody = response.body().trim();

                if (responseBody.startsWith("[")) {
                    return objectMapper.readValue(responseBody, new TypeReference<List<Object>>() {});
                } else if (responseBody.startsWith("{")) {
                    return objectMapper.readValue(responseBody, new TypeReference<Map<String, Object>>() {});
                } else {
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