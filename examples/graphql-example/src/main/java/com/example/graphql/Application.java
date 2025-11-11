package com.example.graphql;

import com.example.graphql.config.DataSourceConfigManager;
import com.example.graphql.config.QueryConfigManager;
import com.example.graphql.datasource.DataFetcherFactory;
import com.example.graphql.datasource.DataSourceType;
import com.example.graphql.datasource.SimpleDataFetcherFactory;
import com.example.graphql.handler.GraphQLHandler;
import com.example.graphql.model.QueryDefinition;
import com.example.graphql.registry.QueryRegistry;
import com.example.graphql.schema.SchemaManager;
import com.example.graphql.server.SimpleGraphQLServer;

import java.util.Map;

public class Application {
    public static void main(String[] args) throws Exception {
        DataSourceConfigManager configManager = new DataSourceConfigManager();
        configManager.loadDataSources("/datasources.json");

        QueryConfigManager queryConfigManager = new QueryConfigManager();
        QueryRegistry queryRegistry = new QueryRegistry();

        System.out.println("=== Query Registration Phase ===");
        queryConfigManager.loadQueriesFromConfig(queryRegistry, "/queries-config.json");
        System.out.println("Loaded " + queryRegistry.getAllQueries().size() + " queries from config");
        registerSampleQueries(queryRegistry);
        System.out.println("Total queries in registry: " + queryRegistry.getAllQueries().size());
        queryRegistry.getAllQueries().forEach(q -> System.out.println("  - " + q.getName()));

        DataFetcherFactory dataFetcherFactory = new SimpleDataFetcherFactory();
        SchemaManager schemaManager = new SchemaManager(queryRegistry, dataFetcherFactory);
        GraphQLHandler graphQLHandler = new GraphQLHandler(queryRegistry, schemaManager);

        // 启动服务器
        System.out.println("Data sources configured: " + configManager.hasDataSources());
        System.out.println("Starting GraphQL Server on port 8080...");
        SimpleGraphQLServer server = new SimpleGraphQLServer(8080, graphQLHandler);
        server.start();
    }

    private static void registerSampleQueries(QueryRegistry registry) {
        QueryDefinition userSimple = new QueryDefinition("getUserSimple",
                "query GetUserSimple($id: ID!) { getUserSimple(id: $id) { id name } }");
        registry.registerQuery(userSimple);

        QueryDefinition postSimple = new QueryDefinition("getPostSimple",
                "query GetPostSimple($id: ID!) { getPostSimple(id: $id) { id title } }");
        registry.registerQuery(postSimple);

    }
}