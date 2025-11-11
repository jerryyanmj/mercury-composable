package com.example.graphql.mock;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class MockHttpServer {
    private HttpServer server;
    private final int port;

    public MockHttpServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newCachedThreadPool());

        // 模拟用户服务
        server.createContext("/users/", new UserHandler());

        // 模拟帖子服务
        server.createContext("/posts/", new PostHandler());

        // 模拟用户帖子服务
        server.createContext("/user-posts/", new UserPostsHandler());

        server.start();
        System.out.println("Mock HTTP Server started on port " + port);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            System.out.println("Mock HTTP Server stopped");
        }
    }

    // 用户处理器
    static class UserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                String path = exchange.getRequestURI().getPath();
                String userId = path.substring(path.lastIndexOf("/") + 1);

                String response = """
                    {
                        "id": %s,
                        "name": "Mock User %s",
                        "email": "user%s@mock.com",
                        "phone": "555-0100-%s",
                        "website": "user%s.example.com",
                        "address": {
                            "street": "123 Mock St",
                            "city": "Mock City"
                        }
                    }
                    """.formatted(userId, userId, userId, userId, userId);

                sendResponse(exchange, response);
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        }
    }

    // 帖子处理器
    static class PostHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                String path = exchange.getRequestURI().getPath();
                String postId = path.substring(path.lastIndexOf("/") + 1);

                String response = """
                    {
                        "id": %s,
                        "title": "Mock Post Title %s",
                        "body": "This is the content of mock post %s. It contains detailed information about various topics.",
                        "userId": %s,
                        "tags": ["tech", "graphql", "mock"],
                        "createdAt": "2024-01-01T10:00:00Z"
                    }
                    """.formatted(postId, postId, postId, Integer.parseInt(postId) % 10 + 1);

                sendResponse(exchange, response);
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        }
    }

    // 用户帖子聚合处理器
    static class UserPostsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                String path = exchange.getRequestURI().getPath();
                String userId = path.substring(path.lastIndexOf("/") + 1);

                String response = """
                    {
                        "user": {
                            "id": %s,
                            "name": "Mock User %s",
                            "email": "user%s@mock.com"
                        },
                        "posts": [
                            {
                                "id": "%s-1",
                                "title": "User %s First Post",
                                "body": "First post content from user %s"
                            },
                            {
                                "id": "%s-2", 
                                "title": "User %s Second Post",
                                "body": "Second post content from user %s"
                            }
                        ]
                    }
                    """.formatted(userId, userId, userId, userId, userId, userId, userId, userId, userId);

                sendResponse(exchange, response);
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        }
    }

    private static void sendResponse(HttpExchange exchange, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }

    private static void sendError(HttpExchange exchange, int code, String message) throws IOException {
        String response = "{\"error\": \"" + message + "\"}";
        exchange.sendResponseHeaders(code, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }
}