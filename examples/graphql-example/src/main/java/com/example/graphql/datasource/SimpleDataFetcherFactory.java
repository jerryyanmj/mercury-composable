package com.example.graphql.datasource;

import com.example.graphql.model.QueryDefinition;
import graphql.schema.DataFetcher;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimpleDataFetcherFactory implements DataFetcherFactory {

    @Override
    public DataFetcher<?> createDataFetcher(QueryDefinition query) {
        return environment -> {
            String id = environment.getArgument("id");

            System.out.println("Executing query: " + query.getName() + " for id: " + id);

            if (query.getName().contains("WithPosts")) {
                return getUserWithPostsData(id);
            } else if (query.getName().contains("Post")) {
                return getPostData(id);
            } else {
                return getUserData(id);
            }
        };
    }

    Map<String, Object> getUserData(String userId) {
        System.out.println("Invoke user source fetcher: /api/user/" + userId);
        return Map.of(
                "id", userId,
                "name", "User " + userId,
                "email", "user" + userId + "@example.com",
                "fullName", "User " + userId + " Full Name",
                "hobbies", List.of("reading", "coding", "gaming"),
                "phone", "123-456-7890",
                "address", "Some Address"
        );
    }

    Map<String, Object> getPostData(String postId) {
        System.out.println("Invoke post source fetcher: /api/post/" + postId);
        return Map.of(
                "id", postId,
                "title", "Post " + postId,
                "content", "This is the content of post " + postId,
                "createdAt", "2024-01-01",
                "updatedAt", "2024-01-02",
                "author", "author_" + postId,
                "tags", List.of("tech", "graphql")
        );
    }

    Map<String, Object> getUserWithPostsData(String userId) {
        System.out.println("Invoke user with posts source fetcher " + userId);
        Map<String, Object> userData = getUserData(userId);
        List<Map<String, Object>> postsData = List.of(
                getPostData("post_1_" + userId),
                getPostData("post_2_" + userId)
        );

        // 合并数据
        Map<String, Object> result = new HashMap<>(userData);
        result.put("posts", postsData);
        return result;
    }

}