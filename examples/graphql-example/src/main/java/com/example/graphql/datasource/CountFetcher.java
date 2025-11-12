package com.example.graphql.datasource;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

import java.util.List;
import java.util.Map;

public class CountFetcher implements DataFetcher<Integer> {

    @Override
    public Integer get(DataFetchingEnvironment environment) {


        // 'source' (or parent) is the object returned by the previous DataFetcher
        // which, in your case, is the 'ConfigurableDataFetcher' returning a Map.
        Map<String, Object> source = environment.getSource();
        System.out.println("CountFetcher running. Source type: " + (source != null ? source.getClass().getName() : "null"));

        if (source != null && source.containsKey("posts")) {
            Object postsObject = source.get("posts");
            // Check if the object is actually a List
            if (postsObject instanceof List) {
                List<?> postsList = (List<?>) postsObject;
                return postsList.size(); // Return the size of the list
            }
        }
        return 0; // Default to 0 if data isn't found or isn't a list
    }
}
