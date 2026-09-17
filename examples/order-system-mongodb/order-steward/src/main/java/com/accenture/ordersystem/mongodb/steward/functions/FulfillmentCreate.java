package com.accenture.ordersystem.mongodb.steward.functions;

import com.accenture.ordersystem.mongodb.common.Db;
import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.TypedLambdaFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * v1.fulfillment.create — idempotent create of a fulfillment document using $setOnInsert.
 * A re-delivered fulfillment.request event is a no-op.
 */
@PreLoad(route = "v1.fulfillment.create", instances = 10)
public class FulfillmentCreate implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(FulfillmentCreate.class);

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers,
                                           Map<String, Object> input, int instance) throws Exception {
        String fulfillmentId = (String) input.get("fulfillment_id");
        String orderId = (String) input.get("order_id");
        Object payload = input.get("payload");
        String instance_cid = (String) input.get("instance");

        if (fulfillmentId == null || !fulfillmentId.startsWith("F")) {
            throw new IllegalArgumentException("fulfillment_id must start with 'F'");
        }
        if (orderId == null) throw new IllegalArgumentException("Missing order_id");

        Db db = Db.using(headers, instance);
        String now = Instant.now().toString();

        Map<String, Object> doc = new HashMap<>();
        doc.put("fulfillment_id", fulfillmentId);
        doc.put("order_id", orderId);
        doc.put("lifecycle_state", "VALIDATED");
        doc.put("payload", payload);
        doc.put("milestones", new HashMap<>());
        doc.put("outbox", List.of());
        doc.put("_seq", 0);
        doc.put("created_at", now);
        doc.put("updated_at", now);

        db.upsertOne("fulfillments",
                Map.of("fulfillment_id", fulfillmentId),
                Map.of("$setOnInsert", doc));

        log.info("Fulfillment {} created (or already existed) for order {}", fulfillmentId, orderId);
        return Map.of("fulfillment_id", fulfillmentId, "order_id", orderId);
    }
}
