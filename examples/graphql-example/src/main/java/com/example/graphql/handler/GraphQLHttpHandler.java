package com.example.graphql.handler;

import com.example.graphql.model.GraphQLRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class GraphQLHttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private final GraphQLHandler graphQLHandler;
    private final ObjectMapper objectMapper;

    public GraphQLHttpHandler(GraphQLHandler handler) {
        this.graphQLHandler = handler;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        if (!request.method().equals(HttpMethod.POST)) {
            sendError(ctx, "Only POST method is supported", HttpResponseStatus.METHOD_NOT_ALLOWED);
            return;
        }

        try {
            String content = request.content().toString(StandardCharsets.UTF_8);
            GraphQLRequest graphQLRequest = parseRequest(content);
            CompletableFuture<String> result = graphQLHandler.execute(graphQLRequest);

            result.whenComplete((response, error) -> {
                if (error != null) {
                    sendError(ctx, "Internal server error: " + error.getMessage(), HttpResponseStatus.INTERNAL_SERVER_ERROR);
                } else {
                    sendJsonResponse(ctx, response);
                }
            });

        } catch (Exception e) {
            sendError(ctx, "Invalid request: " + e.getMessage(), HttpResponseStatus.BAD_REQUEST);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        sendError(ctx, "Server error: " + cause.getMessage(), HttpResponseStatus.INTERNAL_SERVER_ERROR);
    }

    private void sendJsonResponse(ChannelHandlerContext ctx, String json) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                Unpooled.copiedBuffer(json, StandardCharsets.UTF_8));

        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        ctx.writeAndFlush(response);
    }

    private void sendError(ChannelHandlerContext ctx, String message, HttpResponseStatus status) {
        String errorJson = String.format("{\"error\":\"%s\"}", message.replace("\"", "\\\""));
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, status,
                Unpooled.copiedBuffer(errorJson, StandardCharsets.UTF_8));

        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        ctx.writeAndFlush(response);
    }

    private GraphQLRequest parseRequest(String content) throws Exception {
        JsonNode json = objectMapper.readTree(content);
        String queryName = json.get("queryName").asText();
        Map<String, Object> variables = objectMapper.convertValue(
                json.get("variables"), new TypeReference<Map<String, Object>>() {});

        return new GraphQLRequest(queryName, variables);
    }
}
