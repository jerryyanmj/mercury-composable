package com.example.graphql.schema;

import com.example.graphql.datasource.CountFetcher;
import com.example.graphql.datasource.DataFetcherFactory;
import com.example.graphql.model.QueryDefinition;
import com.example.graphql.registry.QueryRegistry;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.DataFetcher;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SchemaManager {
    private final QueryRegistry queryRegistry;
    private final DataFetcherFactory dataFetcherFactory;

    public SchemaManager(QueryRegistry queryRegistry, DataFetcherFactory dataFetcherFactory) {
        this.queryRegistry = queryRegistry;
        this.dataFetcherFactory = dataFetcherFactory;
    }

    public GraphQL buildGraphQL() {
        try {
            // 构建动态 Schema
            String schemaDefinition = buildSchemaDefinition();
            System.out.println("Generated Schema:\n" + schemaDefinition);

            TypeDefinitionRegistry typeRegistry = buildTypeRegistry(schemaDefinition);
            RuntimeWiring runtimeWiring = buildRuntimeWiring();

            SchemaGenerator schemaGenerator = new SchemaGenerator();
            GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);

            System.out.println("=== Schema Info ===");
            System.out.println("Query type fields: " + graphQLSchema.getQueryType().getFieldDefinitions());
            System.out.println("User type fields: " + graphQLSchema.getType("User"));
            System.out.println("Post type fields: " + graphQLSchema.getType("Post"));
            System.out.println("UserWithPosts type fields: " + graphQLSchema.getType("UserWithPosts"));

            return GraphQL.newGraphQL(graphQLSchema).build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build GraphQL schema: " + e.getMessage(), e);
        }
    }

    private String buildSchemaDefinition2() {
        StringBuilder schema = new StringBuilder();

        schema.append("""
        type User {
            id: ID!
            name: String
            email: String
            phone: String
            website: String
        }
        
        type Post {
            id: ID!
            title: String
            content: String
            authorId: ID!
        }
        
        """);

        // 然后定义 Query 类型
        schema.append("type Query {\n");

        Set<String> definedFields = new HashSet<>();
        boolean hasFields = false;

        for (QueryDefinition query : queryRegistry.getAllQueries()) {
            String querySignature = extractQuerySignature(query);
            if (querySignature != null && !querySignature.trim().isEmpty()) {
                String fieldName = extractFieldNameFromSignature(querySignature);
                // 确保字段名不重复
                if (fieldName != null && !definedFields.contains(fieldName)) {
                    schema.append("  ").append(querySignature).append("\n");
                    definedFields.add(fieldName);
                    hasFields = true;
                }
            }
        }

        // 如果没有解析到任何字段，添加一个默认字段防止空 Query
        if (!hasFields) {
            schema.append("  defaultField: String\n");
        }

        schema.append("}\n");

        return schema.toString();
    }

    private String buildSchemaDefinition() {
        StringBuilder schema = new StringBuilder();
        schema.append("type Query {\n");

        System.out.println("=== Schema Generation Debug ===");
        System.out.println("QueryRegistry has " + queryRegistry.getAllQueries().size() + " queries");

        boolean hasFields = false;
        for (QueryDefinition query : queryRegistry.getAllQueries()) {
            System.out.println("Processing query: " + query.getName());
            System.out.println("Query string: " + query.getQueryString());

            String querySignature = extractQuerySignature(query);
            System.out.println("Extracted signature: " + querySignature);

            if (querySignature != null && !querySignature.trim().isEmpty()) {
                schema.append("  ").append(querySignature).append("\n");
                hasFields = true;
                System.out.println("Added to Schema: " + querySignature);
            } else {
                System.out.println("No signature extracted for query: " + query.getName());
            }
        }

        // 如果没有解析到任何字段，添加一个默认字段防止空 Query
        if (!hasFields) {
            System.out.println("WARNING: No query fields found in registry!");
            System.out.println("Registered queries: " + queryRegistry.getAllQueries().size());
            schema.append("  defaultField: String\n");
        }

        schema.append("}\n");


        // 首先定义类型
        schema.append("""
            type User {
                id: ID!
                name: String
                email: String
                fullName: String
                hobbies: [String]
                phone: String
                address: String
            }

            type Post {
                id: ID!
                title: String
                content: String
                createdAt: String
                updatedAt: String
                author: String
                tags: [String]
            }

            type PostRating {
                id: ID!
                postId: String
                totalRatings: Float
                createdAt: String
                updatedAt: String
                averageRating: Float
            }

            type PostWithRating {
                id: ID!
                postId: String
                title: String
                content: String
                author: String
                tags: [String]
                totalRatings: Float
                createdAt: String
                updatedAt: String
                averageRating: Float
            }

            type UserWithPosts {
                id: ID!
                name: String
                email: String
                fullName: String
                hobbies: [String]
                phone: String
                address: String
                posts: [Post]
                totalCount: Int
            }
        """);

        String finalSchema = schema.toString();
        System.out.println("=== Final Generated Schema ===");
        System.out.println(finalSchema);
        System.out.println("=== End Schema ===");

        return finalSchema;
    }

    private RuntimeWiring buildRuntimeWiring() {
        RuntimeWiring.Builder wiringBuilder = RuntimeWiring.newRuntimeWiring();

        Set<String> registeredFields = new HashSet<>();

        // Register DataFetcher
        for (QueryDefinition query : queryRegistry.getAllQueries()) {
            String fieldName = extractFieldName(query.getQueryString());
            if (fieldName != null && !registeredFields.contains(fieldName)) {
                DataFetcher<?> dataFetcher = dataFetcherFactory.createDataFetcher(query);
                wiringBuilder.type("Query", builder ->
                        builder.dataFetcher(fieldName, dataFetcher));
                registeredFields.add(fieldName);
                System.out.println("Registered DataFetcher for field: " + fieldName);
            }
        }

        // Add a default one
        if (registeredFields.isEmpty()) {
            wiringBuilder.type("Query", builder ->
                    builder.dataFetcher("defaultField", environment -> "Default value"));
        }

        // Add count
        wiringBuilder.type("UserWithPosts", builder ->
            builder.dataFetcher("totalCount", new CountFetcher())
        );

        return wiringBuilder.build();
    }

    private TypeDefinitionRegistry buildTypeRegistry(String schema) {
        SchemaParser parser = new SchemaParser();
        return parser.parse(schema);
    }

    private String extractQuerySignature(QueryDefinition query) {
        String queryString = query.getQueryString();
        String queryName = query.getName();

        // 使用查询配置的名称作为字段名，确保唯一性
        String fieldName = queryName;

        // 从查询字符串中提取参数信息
        Pattern paramPattern = Pattern.compile("\\$\\w+\\s*:\\s*(\\w+!?)");
        Matcher paramMatcher = paramPattern.matcher(queryString);

        String paramType = "ID!"; // 默认参数类型
        if (paramMatcher.find()) {
            paramType = paramMatcher.group(1);
        }

        // 根据查询内容推断返回类型
        String returnType = inferReturnType(query);

        return String.format("%s(id: %s): %s", fieldName, paramType, returnType);
    }

    private String extractFieldNameFromSignature(String signature) {
        // 从签名中提取字段名，例如 "getUser(id: ID!): User" -> "getUser"
        if (signature == null) return null;
        int parenIndex = signature.indexOf('(');
        return parenIndex > 0 ? signature.substring(0, parenIndex).trim() : signature.trim();
    }

    private String extractFieldName(String queryString) {
        // 使用查询配置的名称作为字段名，而不是从查询字符串中提取
        // 这样可以确保字段名唯一且有意义
        for (QueryDefinition query : queryRegistry.getAllQueries()) {
            if (query.getQueryString().equals(queryString)) {
                return query.getName();
            }
        }
        return null;
    }

    private String inferReturnType(QueryDefinition query) {
        String queryString = query.getQueryString();
        String queryName = query.getName();

        // 基于查询名称和内容推断返回类型
        if (queryName.contains("WithPosts")) {
            return "UserWithPosts";
        } else if (queryString.contains("Rating") && queryString.contains("title")) {
            return "PostWithRating";
        } else if (queryString.contains("title") || queryString.contains("content")) {
            return "Post";
        } else if (queryString.contains("Rating")) {
            return "PostRating";
        } else {
            return "User";
        }
    }
}