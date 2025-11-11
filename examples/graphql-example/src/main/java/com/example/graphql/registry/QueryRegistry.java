package com.example.graphql.registry;

import com.example.graphql.model.QueryDefinition;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class QueryRegistry {
    private final Map<String, QueryDefinition> queries = new ConcurrentHashMap<>();

    public void registerQuery(QueryDefinition query) {
        queries.put(query.getName(), query);
    }

    public QueryDefinition getQuery(String name) {
        return queries.get(name);
    }

    public boolean removeQuery(String name) {
        return queries.remove(name) != null;
    }

    public Collection<QueryDefinition> getAllQueries() {
        return queries.values();
    }

    public boolean containsQuery(String name) {
        return queries.containsKey(name);
    }
}