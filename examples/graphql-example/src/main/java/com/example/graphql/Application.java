package com.example.graphql;

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
        // 初始化核心组件
        QueryRegistry queryRegistry = new QueryRegistry();
        SimpleDataFetcherFactory dataFetcherFactory = new SimpleDataFetcherFactory();

        // 先注册查询，再创建 SchemaManager
        registerSampleQueries(queryRegistry);

        SchemaManager schemaManager = new SchemaManager(queryRegistry, dataFetcherFactory);
        GraphQLHandler graphQLHandler = new GraphQLHandler(queryRegistry, schemaManager);

        // 启动服务器
        System.out.println("Starting GraphQL Server on port 8080...");
        SimpleGraphQLServer server = new SimpleGraphQLServer(8080, graphQLHandler);
        server.start();
    }

    private static void registerSampleQueries(QueryRegistry registry) {


        QueryDefinition userSimple = new QueryDefinition();
        userSimple.setName("getUserSimple");
        userSimple.setQueryString("query GetUserSimple($id: ID!) { getUserSimple(id: $id) { id name } }");
        userSimple.setDataSourceType(DataSourceType.USER);
        registry.registerQuery(userSimple);

        QueryDefinition userFull = new QueryDefinition();
        userFull.setName("getUserFull");
        userFull.setQueryString("query GetUserFull($id: ID!) { getUserFull(id: $id) { id name email fullName hobbies phone address } }");
        userFull.setDataSourceType(DataSourceType.USER);
        registry.registerQuery(userFull);

        QueryDefinition postSimple = new QueryDefinition();
        postSimple.setName("getPostSimple");
        postSimple.setQueryString("query GetPostSimple($id: ID!) { getPostSimple(id: $id) { id title } }");
        postSimple.setDataSourceType(DataSourceType.POST);
        registry.registerQuery(postSimple);

        QueryDefinition postFull = new QueryDefinition();
        postFull.setName("getPostFull");
        postFull.setQueryString("query GetPostFull($id: ID!) { getPostFull(id: $id) { id title content createdAt author tags } }");
        postFull.setDataSourceType(DataSourceType.POST);
        registry.registerQuery(postFull);

        QueryDefinition userWithPosts = new QueryDefinition();
        userWithPosts.setName("getUserWithPosts");
        userWithPosts.setQueryString("query GetUserWithPosts($id: ID!) { getUserWithPosts(id: $id) { id name email posts { id title } } }");
        userWithPosts.setDataSourceType(DataSourceType.USER_WITH_POSTS);
        registry.registerQuery(userWithPosts);

        System.out.println("Registered " + registry.getAllQueries().size() + " queries:");
        registry.getAllQueries().forEach(q -> System.out.println("  - " + q.getName()));    }
}