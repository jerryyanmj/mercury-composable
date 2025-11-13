package com.example.graphql.handler;

import com.example.graphql.model.GraphQLRequest;
import com.example.graphql.model.QueryDefinition;
import com.example.graphql.registry.QueryRegistry;
import com.example.graphql.schema.SchemaManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionInput;
import graphql.GraphQL;

import java.util.concurrent.CompletableFuture;

public class GraphQLHandler {
    private final QueryRegistry queryRegistry;
    private final SchemaManager schemaManager;
    private final ObjectMapper objectMapper;
    private GraphQL graphQL;

    public GraphQLHandler(QueryRegistry queryRegistry, SchemaManager schemaManager) {
        this.queryRegistry = queryRegistry;
        this.schemaManager = schemaManager;
        this.objectMapper = new ObjectMapper();
        refreshSchema();
    }

    public CompletableFuture<String> execute(GraphQLRequest request) {
        try {
            // Get Query from registry
            QueryDefinition queryDef = queryRegistry.getQuery(request.getQueryName());
            if (queryDef == null) {
                throw new RuntimeException("Query not found: " + request.getQueryName());
            }

            System.out.println("=== GraphQL Execution Debug ===");
            System.out.println("Query Name: " + request.getQueryName());
            System.out.println("Query String: " + queryDef.getQueryString());
            System.out.println("Variables: " + request.getVariables());

            ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                    .query(queryDef.getQueryString())
                    .variables(request.getVariables())
                    .build();

            return graphQL.executeAsync(executionInput)
                    .thenApply(result -> {
                        if (result.getErrors() != null && !result.getErrors().isEmpty()) {
                            System.out.println("GraphQL Execution Errors:");
                            result.getErrors().forEach(error -> {
                                System.out.println("  - " + error.getMessage());
                                System.out.println("  - Locations: " + error.getLocations());
                                if (error.getExtensions() != null) {
                                    System.out.println("  - Extensions: " + error.getExtensions());
                                }
                            });
                        }
                        return result.toSpecification();
                    })
                    .thenApply(this::toJson);

        } catch (Exception e) {
            CompletableFuture<String> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    public void refreshSchema() {
        this.graphQL = schemaManager.buildGraphQL();
    }

    private String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            return "{\"error\":\"JSON serialization failed: " + e.getMessage() + "\"}";
        }
    }
}