package com.example.graphql.datasource;

import com.example.graphql.model.QueryDefinition;
import graphql.schema.DataFetcher;

public interface DataFetcherFactory {
    DataFetcher<?> createDataFetcher(QueryDefinition query);
}