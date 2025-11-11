package com.example.graphql;

import com.example.graphql.config.DataSourceConfigManager;
import com.example.graphql.datasource.ConfigurableDataFetcherFactory;
import com.example.graphql.datasource.DataFetcherFactory;
import com.example.graphql.handler.GraphQLHandler;
import com.example.graphql.mock.MockHttpServer;
import com.example.graphql.model.QueryDefinition;
import com.example.graphql.registry.QueryRegistry;
import com.example.graphql.schema.SchemaManager;
import com.example.graphql.server.SimpleGraphQLServer;

public class Application2 {
    private static MockHttpServer mockServer;

    public static void main(String[] args) throws Exception {

        mockServer = new MockHttpServer(9090);
        mockServer.start();

        DataSourceConfigManager configManager = new DataSourceConfigManager();
        configManager.loadDataSources("/datasources.json");

        // 初始化核心组件
        QueryRegistry queryRegistry = new QueryRegistry();
        DataFetcherFactory dataFetcherFactory = new ConfigurableDataFetcherFactory(configManager);

        // 先注册查询，再创建 SchemaManager
        registerSampleQueries(queryRegistry);

        SchemaManager schemaManager = new SchemaManager(queryRegistry, dataFetcherFactory);
        GraphQLHandler graphQLHandler = new GraphQLHandler(queryRegistry, schemaManager);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            mockServer.stop();
        }));
        // 启动服务器
        System.out.println("Starting GraphQL Server on port 8080...");
        SimpleGraphQLServer server = new SimpleGraphQLServer(8080, graphQLHandler);
        server.start();
    }

    private static void registerSampleQueries(QueryRegistry registry) {

        // 这些查询现在会使用真实的 REST API！
        QueryDefinition userQuery = new QueryDefinition();
        userQuery.setName("getRealUser");
        userQuery.setQueryString("query GetRealUser($id: ID!) { getRealUser(id: $id) { id name email phone website } }");
        registry.registerQuery(userQuery);

        QueryDefinition postQuery = new QueryDefinition();
        postQuery.setName("getRealPost");
        postQuery.setQueryString("query GetRealPost($id: ID!) { getRealPost(id: $id) { id title content authorId } }");
        registry.registerQuery(postQuery);

        System.out.println("Registered " + registry.getAllQueries().size() + " queries:");
        registry.getAllQueries().forEach(q -> System.out.println("  - " + q.getName()));    }
}