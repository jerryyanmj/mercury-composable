package com.accenture.ordersystem.mongodb.common;

import org.platformlambda.core.models.EventEnvelope;
import org.platformlambda.core.system.PostOffice;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

/**
 * Convenience facade over the mongo.service route.
 * All blocking calls are safe on virtual threads.
 */
public class Db {
    private static final String MONGO_SERVICE = "mongo.service";
    private static final long TIMEOUT_MS = 30_000;

    private final PostOffice po;

    private Db(PostOffice po) {
        this.po = po;
    }

    public static Db using(Map<String, String> headers, int instance) throws Exception {
        PostOffice po = new PostOffice(headers, instance);
        return new Db(po);
    }

    public PostOffice postOffice() {
        return po;
    }

    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> findOne(String collection,
                                                  Map<String, Object> filter) throws Exception {
        Map<String, Object> result = (Map<String, Object>) send("findOne", collection, filter,
                null, false, false, null, 0);
        return (result == null || result.isEmpty()) ? Optional.empty() : Optional.of(result);
    }

    public Map<String, Object> findOneOrFail(String collection,
                                              Map<String, Object> filter,
                                              String notFound) throws Exception {
        return findOne(collection, filter).orElseThrow(() -> new IllegalStateException(notFound));
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> findMany(String collection,
                                               Map<String, Object> filter) throws Exception {
        return findMany(collection, filter, 0);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> findMany(String collection,
                                               Map<String, Object> filter,
                                               int limit) throws Exception {
        Object result = send("find", collection, filter, null, false, false, null, limit);
        if (result instanceof List<?>) return (List<Map<String, Object>>) result;
        return Collections.emptyList();
    }

    /**
     * Insert with $setOnInsert semantics: creates the doc if absent, no-op if present.
     * The update map should be {"$setOnInsert": {...}}.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> upsertOne(String collection,
                                          Map<String, Object> filter,
                                          Map<String, Object> update) throws Exception {
        Object result = send("updateOne", collection, filter, update, true, false, null, 0);
        if (result instanceof Map<?, ?>) return (Map<String, Object>) result;
        return Collections.emptyMap();
    }

    /**
     * Finds one doc matching filter, applies update (plain map or pipeline list), and
     * optionally returns the document before or after modification.
     *
     * @param returnAfter  true → return document AFTER update; false → BEFORE
     * @param upsert       create if no match
     * @param arrayFilters list of filter conditions for array element matching, or null
     */
    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> findOneAndUpdate(String collection,
                                                           Map<String, Object> filter,
                                                           Object update,
                                                           boolean returnAfter,
                                                           boolean upsert,
                                                           List<Map<String, Object>> arrayFilters)
            throws Exception {
        Map<String, Object> stmt = buildStmt("findOneAndUpdate", collection, filter, update,
                upsert, returnAfter, arrayFilters, 0);
        Object result = call(stmt);
        if (result instanceof Map<?, ?> m && !m.isEmpty()) return Optional.of((Map<String, Object>) m);
        return Optional.empty();
    }

    public void updateOne(String collection,
                           Map<String, Object> filter,
                           Object update,
                           List<Map<String, Object>> arrayFilters) throws Exception {
        send("updateOne", collection, filter, update, false, false, arrayFilters, 0);
    }

    // ── internal ──────────────────────────────────────────────────────────────

    private Object send(String operation, String collection, Map<String, Object> filter,
                        Object update, boolean upsert, boolean returnAfter,
                        List<Map<String, Object>> arrayFilters, int limit) throws Exception {
        return call(buildStmt(operation, collection, filter, update, upsert, returnAfter, arrayFilters, limit));
    }

    private Map<String, Object> buildStmt(String operation, String collection,
                                           Map<String, Object> filter, Object update,
                                           boolean upsert, boolean returnAfter,
                                           List<Map<String, Object>> arrayFilters, int limit) {
        Map<String, Object> stmt = new HashMap<>();
        stmt.put("operation", operation);
        stmt.put("collection", collection);
        if (filter != null) stmt.put("filter", filter);
        if (update != null) stmt.put("update", update);
        if (upsert) stmt.put("upsert", true);
        if (returnAfter) stmt.put("returnAfter", true);
        if (arrayFilters != null && !arrayFilters.isEmpty()) stmt.put("arrayFilters", arrayFilters);
        if (limit > 0) stmt.put("limit", limit);
        return stmt;
    }

    private Object call(Map<String, Object> stmt) throws Exception {
        EventEnvelope env = new EventEnvelope().setTo(MONGO_SERVICE).setBody(stmt);
        EventEnvelope response = po.request(env, TIMEOUT_MS).get();
        if (response.getStatus() >= 400) {
            throw new RuntimeException("mongo.service error " + response.getStatus() +
                    ": " + response.getBody());
        }
        return response.getBody();
    }
}
