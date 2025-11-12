package com.example.graphql;

import com.example.graphql.config.DataSourceConfig;
import com.example.graphql.config.DataSourceConfigManager;
import com.example.graphql.config.EndpointConfig;
import com.example.graphql.config.QueryConfigManager;
import com.example.graphql.datasource.ConfigurableDataFetcherFactory;
import com.example.graphql.datasource.DataFetcherFactory;
import com.example.graphql.datasource.DataSourceType;
import com.example.graphql.datasource.SimpleDataFetcherFactory;
import com.example.graphql.handler.GraphQLHandler;
import com.example.graphql.mock.MockHttpServer;
import com.example.graphql.model.QueryDefinition;
import com.example.graphql.registry.QueryRegistry;
import com.example.graphql.schema.SchemaManager;
import com.example.graphql.server.SimpleGraphQLServer;

import java.util.Map;

public class Application {

    private static MockHttpServer mockServer;

    public static void main(String[] args) throws Exception {
        mockServer = new MockHttpServer(9090);
        mockServer.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (mockServer != null) {
                mockServer.stop();
            }
        }));

        Thread.sleep(3000);

        DataSourceConfigManager dataSourceConfigManager = new DataSourceConfigManager();
        dataSourceConfigManager.loadDataSources("/datasources.json");

        System.out.println("=== Data Source Configuration ===");
        Map<String, DataSourceConfig> allDataSources = dataSourceConfigManager.getAllDataSources();
        if (allDataSources.isEmpty()) {
            System.err.println("WARNING: No data sources loaded from configuration!");
        } else {
            System.out.println("Loaded data sources: " + allDataSources.keySet());
            for (Map.Entry<String, DataSourceConfig> entry : allDataSources.entrySet()) {
                DataSourceConfig config = entry.getValue();
                System.out.println("  - " + entry.getKey() + ": " + config.getBaseUrl());
                System.out.println("    Endpoints: " + config.getEndpoints().keySet());

                for (String endpointName : config.getEndpoints().keySet()) {
                    EndpointConfig endpoint = config.getEndpoint(endpointName);
                    System.out.println("       ↳ " + endpointName + ": " + endpoint.getPath() +
                            " -> " + endpoint.getResponseMapping().keySet());
                }
            }
        }

        QueryConfigManager queryConfigManager = new QueryConfigManager();
        QueryRegistry queryRegistry = new QueryRegistry();

        System.out.println("=== Query Registration Phase ===");
        queryConfigManager.loadQueriesFromConfig(queryRegistry, "/queries-config.json");
        System.out.println("Loaded " + queryRegistry.getAllQueries().size() + " queries from config");
        registerSampleQueries(queryRegistry);
        System.out.println("Total queries in registry: " + queryRegistry.getAllQueries().size());
        queryRegistry.getAllQueries().forEach(q -> System.out.println("  - " + q.getName()));

        System.out.println("=== Schema Initialization Phase ===");
        DataFetcherFactory dataFetcherFactory = new ConfigurableDataFetcherFactory(dataSourceConfigManager);
        SchemaManager schemaManager = new SchemaManager(queryRegistry, dataFetcherFactory);
        GraphQLHandler graphQLHandler = new GraphQLHandler(queryRegistry, schemaManager);

        System.out.println("Data sources configured: " + dataSourceConfigManager.hasDataSources());

        // Start server
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