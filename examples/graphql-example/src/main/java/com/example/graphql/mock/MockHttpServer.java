// com/example/graphql/mock/EnhancedMockHttpServer.java
package com.example.graphql.mock;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public class MockHttpServer {
    private HttpServer server;
    private final int port;

    // 模拟数据存储
    private final Map<String, Map<String, Object>> userDatabase = new HashMap<>();
    private final Map<String, Map<String, Object>> postDatabase = new HashMap<>();
    private final Map<String, Map<String, Object>> postRatingDatabase = new HashMap<>();
    private final Map<String, List<String>> userPostsDatabase = new HashMap<>();

    public MockHttpServer(int port) {
        this.port = port;
        initializeMockData();
    }

    private void initializeMockData() {
        // 初始化用户数据
        for (int i = 1; i <= 5; i++) {
            String userId = String.valueOf(i);
            userDatabase.put(userId, Map.of(
                    "id", userId,
                    "name", "Mock User " + i,
                    "email", "user" + i + "@mock.com",
                    "fullName", "Mock User " + i + " Full Name",
                    "hobbies", List.of("reading", "coding", "gaming", "hobby" + i),
                    "phone", "555-010" + i,
                    "address", "Mock Address " + i
            ));
        }

        // 初始化帖子数据
        int postId = 1;
        for (int userId = 1; userId <= 5; userId++) {
            List<String> userPostIds = new ArrayList<>();
            for (int j = 1; j <= 3; j++) {
                String postIdStr = String.valueOf(postId);
                postDatabase.put(postIdStr, Map.of(
                        "id", postIdStr,
                        "title", "Post " + postId + " by User " + userId,
                        "content", "This is the content of post " + postId + ". It contains detailed information about various topics related to user " + userId + ".",
                        "createdAt", "2024-01-" + (postId % 30 + 1),
                        "updatedAt", "2024-01-" + (postId % 30 + 2),
                        "author", "author_" + userId,
                        "tags", List.of("tag" + j, "tech", "graphql")
                ));
                postRatingDatabase.put(postIdStr, Map.of(
                        "id", "rating_" + postId,
                        "postId", postId,
                        "averageRating", calculateAverageRating(postIdStr),
                        "totalRatings", calculateTotalRatings(postIdStr),
                        "createdAt", "2024-01-15T10:30:00Z",
                        "updatedAt", "2024-01-20T14:45:00Z"
                ));
                userPostIds.add(postIdStr);
                postId++;
            }
            userPostsDatabase.put(String.valueOf(userId), userPostIds);
        }

        System.out.println("Mock data initialized:");
        System.out.println("  - Users: " + userDatabase.keySet());
        System.out.println("  - Posts: " + postDatabase.keySet());
        System.out.println("  - User posts mapping: " + userPostsDatabase);
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newCachedThreadPool());

        // 注册API端点
        server.createContext("/api/user/", new UserHandler());
        server.createContext("/api/post/", new PostHandler());
        server.createContext("/api/post-rating/", new PostRatingHandler());
        server.createContext("/api/posts", new UserPostsHandler());

        server.start();
        System.out.println("Enhanced Mock HTTP Server started on port " + port);
        System.out.println("Available endpoints:");
        System.out.println("  GET /api/user/{id}");
        System.out.println("  GET /api/post/{id}");
        System.out.println("  GET /api/post-rating/{id}");
        System.out.println("  GET /api/posts?userId={userId}");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            System.out.println("Enhanced Mock HTTP Server stopped");
        }
    }

    // 用户处理器
    class UserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                String path = exchange.getRequestURI().getPath();
                String userId = path.substring("/api/user/".length());

                System.out.println("MockServer: GET /api/user/" + userId);

                Map<String, Object> user = userDatabase.get(userId);
                if (user != null) {
                    String response = toJson(user);
                    sendResponse(exchange, response);
                } else {
                    sendError(exchange, 404, "User not found: " + userId);
                }
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        }
    }

    // 单个帖子处理器
    class PostHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                String path = exchange.getRequestURI().getPath();
                String postId = path.substring("/api/post/".length());

                System.out.println("MockServer: GET /api/post/" + postId);

                Map<String, Object> post = postDatabase.get(postId);
                if (post != null) {
                    String response = toJson(post);
                    sendResponse(exchange, response);
                } else {
                    sendError(exchange, 404, "Post not found: " + postId);
                }
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        }
    }

    class PostRatingHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                String path = exchange.getRequestURI().getPath();
                String postId = path.substring("/api/post-rating/".length());

                System.out.println("MockServer: GET /api/post-rating/" + postId);

                Map<String, Object> post = postRatingDatabase.get(postId);
                if (post != null) {
                    String response = toJson(post);
                    sendResponse(exchange, response);
                } else {
                    sendError(exchange, 404, "Post not found: " + postId);
                }
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        }
    }

    // 用户帖子列表处理器
    class UserPostsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                String query = exchange.getRequestURI().getQuery();
                String userId = extractUserIdFromQuery(query);

                System.out.println("MockServer: GET /api/posts?userId=" + userId);

                if (userId != null) {
                    List<String> postIds = userPostsDatabase.get(userId);
                    if (postIds != null) {
                        List<Map<String, Object>> posts = new ArrayList<>();
                        for (String postId : postIds) {
                            posts.add(postDatabase.get(postId));
                        }
                        String response = toJson(posts);
                        sendResponse(exchange, response);
                    } else {
                        sendResponse(exchange, toJson(List.of()));
                    }
                } else {
                    sendError(exchange, 400, "Missing userId parameter");
                }
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        }

        private String extractUserIdFromQuery(String query) {
            if (query != null && query.startsWith("userId=")) {
                return query.substring("userId=".length());
            }
            return null;
        }
    }

    private String toJson(Object obj) {
        // 简化的JSON序列化
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(entry.getKey()).append("\":");
                sb.append(toJson(entry.getValue()));
                first = false;
            }
            sb.append("}");
            return sb.toString();
        } else if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(",");
                sb.append(toJson(item));
                first = false;
            }
            sb.append("]");
            return sb.toString();
        } else if (obj instanceof String) {
            return "\"" + obj.toString().replace("\"", "\\\"") + "\"";
        } else {
            return String.valueOf(obj);
        }
    }

    private void sendResponse(HttpExchange exchange, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }

    private void sendError(HttpExchange exchange, int code, String message) throws IOException {
        String response = "{\"error\": \"" + message + "\"}";
        exchange.sendResponseHeaders(code, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }

    private double calculateAverageRating(String postId) {
        int hash = postId.hashCode();
        return 3.5 + (Math.abs(hash % 16) / 10.0); // 3.5 到 5.0 之间的值
    }

    private int calculateTotalRatings(String postId) {
        int hash = postId.hashCode();
        return Math.abs(hash % 100) + 5; // 5 到 105 之间的值
    }
}