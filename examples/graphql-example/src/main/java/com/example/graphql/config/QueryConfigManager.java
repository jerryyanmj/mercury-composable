package com.example.graphql.config;

import com.example.graphql.model.QueryDefinition;
import com.example.graphql.registry.QueryRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.Map;

public class QueryConfigManager {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void loadQueriesFromConfig(QueryRegistry queryRegistry, String configPath) {
        try (InputStream is = getClass().getResourceAsStream(configPath)) {
            if (is == null) {
                System.out.println("Query config file not found: " + configPath + ", using default queries");
                return;
            }

            JsonNode root = objectMapper.readTree(is);
            JsonNode queriesNode = root.get("queries");

            if (queriesNode != null && queriesNode.isArray()) {
                int loadedCount = 0;
                for (JsonNode queryNode : queriesNode) {
                    String name = queryNode.get("name").asText();
                    String queryString = queryNode.get("queryString").asText();
                    String returnSchema = queryNode.get("returnSchema").asText();
                    JsonNode dataSourcesNode = queryNode.get("dataSources");

                    Map<String, String> dataSources = null;
                    if (dataSourcesNode != null) {
                        dataSources = objectMapper.convertValue(dataSourcesNode,
                                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));
                    }

                    QueryDefinition query = new QueryDefinition(name, queryString, returnSchema, dataSources);
                    queryRegistry.registerQuery(query);
                    loadedCount++;

                    System.out.println("Loaded query from config: " + name + " with dataSources: " + dataSources);
                }
                System.out.println("Successfully loaded " + loadedCount + " queries from config");
            }

        } catch (Exception e) {
            System.err.println("Failed to load query config: " + configPath + ", error: " + e.getMessage());
        }
    }
}