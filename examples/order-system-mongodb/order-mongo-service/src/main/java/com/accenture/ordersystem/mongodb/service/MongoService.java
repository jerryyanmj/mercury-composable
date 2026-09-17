package com.accenture.ordersystem.mongodb.service;

import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoCollection;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.TypedLambdaFunction;
import org.platformlambda.core.util.AppConfigReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@PreLoad(route = "mongo.service", instances = 20)
public class MongoService implements TypedLambdaFunction<Map<String, Object>, Object> {
    private static final Logger log = LoggerFactory.getLogger(MongoService.class);

    private static volatile MongoClient sharedClient;
    private static volatile String sharedDatabase;

    private final MongoClient client;
    private final String database;

    public MongoService() {
        if (sharedClient == null) {
            synchronized (MongoService.class) {
                if (sharedClient == null) {
                    AppConfigReader cfg = AppConfigReader.getInstance();
                    String uri = cfg.getProperty("spring.data.mongodb.uri", "mongodb://localhost:27017");
                    sharedDatabase = cfg.getProperty("spring.data.mongodb.database", "order_system");
                    sharedClient = MongoClients.create(uri);
                    log.info("Connected to MongoDB database '{}'", sharedDatabase);
                }
            }
        }
        this.client = sharedClient;
        this.database = sharedDatabase;
    }

    @Override
    public Object handleEvent(Map<String, String> headers, Map<String, Object> input, int instance) {
        String operation = (String) input.get("operation");
        String collection = (String) input.get("collection");
        if (collection == null) throw new IllegalArgumentException("Missing collection");

        MongoCollection<Document> col = client.getDatabase(database).getCollection(collection);

        return switch (operation) {
            case "findOne"          -> findOne(col, input);
            case "find"             -> find(col, input);
            case "findOneAndUpdate" -> findOneAndUpdate(col, input);
            case "updateOne"        -> updateOne(col, input);
            default -> throw new IllegalArgumentException("Unknown operation: " + operation);
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findOne(MongoCollection<Document> col, Map<String, Object> input) {
        Document filter = toDocument(input.get("filter"));
        Document doc = Mono.from(col.find(filter).limit(1).first()).block();
        return doc == null ? Collections.emptyMap() : toMap(doc);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> find(MongoCollection<Document> col, Map<String, Object> input) {
        Document filter = toDocument(input.get("filter"));
        int limit = input.get("limit") instanceof Number n ? n.intValue() : 0;
        var query = col.find(filter);
        if (limit > 0) query = query.limit(limit);
        List<Document> docs = Flux.from(query).collectList().block();
        if (docs == null || docs.isEmpty()) return Collections.emptyList();
        return docs.stream().map(this::toMap).toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findOneAndUpdate(MongoCollection<Document> col, Map<String, Object> input) {
        Document filter = toDocument(input.get("filter"));
        boolean returnAfter = Boolean.TRUE.equals(input.get("returnAfter"));
        boolean upsert = Boolean.TRUE.equals(input.get("upsert"));

        FindOneAndUpdateOptions opts = new FindOneAndUpdateOptions()
                .returnDocument(returnAfter ? ReturnDocument.AFTER : ReturnDocument.BEFORE)
                .upsert(upsert);

        List<Map<String, Object>> arrayFilters = (List<Map<String, Object>>) input.get("arrayFilters");
        if (arrayFilters != null && !arrayFilters.isEmpty()) {
            opts.arrayFilters(toDocumentList(arrayFilters));
        }

        Object update = input.get("update");
        Document result;
        if (update instanceof List<?> pipelineList) {
            result = Mono.from(col.findOneAndUpdate(filter, toPipeline(pipelineList), opts)).block();
        } else {
            result = Mono.from(col.findOneAndUpdate(filter, toDocument(update), opts)).block();
        }
        return result == null ? Collections.emptyMap() : toMap(result);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> updateOne(MongoCollection<Document> col, Map<String, Object> input) {
        Document filter = toDocument(input.get("filter"));
        boolean upsert = Boolean.TRUE.equals(input.get("upsert"));

        UpdateOptions opts = new UpdateOptions().upsert(upsert);

        List<Map<String, Object>> arrayFilters = (List<Map<String, Object>>) input.get("arrayFilters");
        if (arrayFilters != null && !arrayFilters.isEmpty()) {
            opts.arrayFilters(toDocumentList(arrayFilters));
        }

        Object update = input.get("update");
        com.mongodb.client.result.UpdateResult res;
        if (update instanceof List<?> pipelineList) {
            res = Mono.from(col.updateOne(filter, toPipeline(pipelineList), opts)).block();
        } else {
            res = Mono.from(col.updateOne(filter, toDocument(update), opts)).block();
        }
        Map<String, Object> result = new java.util.HashMap<>();
        if (res != null) {
            result.put("matched_count", res.getMatchedCount());
            result.put("modified_count", res.getModifiedCount());
            result.put("upserted", res.getUpsertedId() != null);
        }
        return result;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Document toDocument(Object obj) {
        if (obj instanceof Map<?, ?> m) return new Document((Map<String, Object>) m);
        return new Document();
    }

    @SuppressWarnings("unchecked")
    private List<? extends Bson> toPipeline(List<?> list) {
        List<Document> pipeline = new ArrayList<>(list.size());
        for (Object stage : list) {
            pipeline.add(toDocument(stage));
        }
        return pipeline;
    }

    @SuppressWarnings("unchecked")
    private List<? extends Bson> toDocumentList(List<Map<String, Object>> list) {
        return list.stream().map(m -> (Bson) new Document(m)).toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Document doc) {
        Map<String, Object> map = new java.util.HashMap<>(doc);
        map.remove("_id");
        map.entrySet().forEach(e -> {
            if (e.getValue() instanceof Document d) e.setValue(toMap(d));
            else if (e.getValue() instanceof List<?> list) e.setValue(convertList(list));
        });
        return map;
    }

    @SuppressWarnings("unchecked")
    private List<Object> convertList(List<?> list) {
        List<Object> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Document d) result.add(toMap(d));
            else if (item instanceof List<?> nested) result.add(convertList(nested));
            else result.add(item);
        }
        return result;
    }
}
