package com.accenture.ordersystem.mongodb.manager.functions;

import com.accenture.ordersystem.mongodb.common.Db;
import com.accenture.ordersystem.mongodb.common.Events;
import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.TypedLambdaFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@PreLoad(route = "v1.order.persist", instances = 10)
public class OrderPersist implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(OrderPersist.class);

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers,
                                           Map<String, Object> input, int instance) throws Exception {
        String orderId = (String) input.get("order_id");
        String fulfillmentId = (String) input.get("fulfillment_id");
        String customerId = (String) input.get("customer_id");
        String now = Instant.now().toString();
        String outboxId = UUID.randomUUID().toString();

        Db db = Db.using(headers, instance);

        // Wrap the order fields under "payload" so the fulfillment-lifecycle graph
        // can reach them as input.body.payload.* (used by classify and validate nodes)
        Map<String, Object> orderPayload = new HashMap<>(input);
        orderPayload.put("submitted_at", now);

        // Outbox event: top-level has routing ids; "payload" is the order data
        Map<String, Object> outboxPayload = new HashMap<>();
        outboxPayload.put("order_id", orderId);
        outboxPayload.put("fulfillment_id", fulfillmentId);
        outboxPayload.put("payload", orderPayload);

        Map<String, Object> outboxEntry = new HashMap<>();
        outboxEntry.put("id", outboxId);
        outboxEntry.put("topic", Events.FULFILLMENT_REQUEST);
        outboxEntry.put("payload", outboxPayload);
        outboxEntry.put("sent_at", null);

        Map<String, Object> orderDoc = new HashMap<>();
        Map<String, Object> initialLine = new HashMap<>();
        initialLine.put("fulfillment_id", fulfillmentId);
        initialLine.put("line_status", "PENDING");

        orderDoc.put("order_id", orderId);
        orderDoc.put("fulfillment_id", fulfillmentId);
        orderDoc.put("customer_id", customerId);
        orderDoc.put("order_status", "PENDING");
        orderDoc.put("status_version", 0);
        orderDoc.put("lines", List.of(initialLine));
        orderDoc.put("history", List.of());
        orderDoc.put("outbox", List.of(outboxEntry));
        orderDoc.put("created_at", now);
        orderDoc.put("updated_at", now);

        Map<String, Object> orderFilter = Map.of("order_id", orderId);
        db.upsertOne("orders", orderFilter, Map.of("$setOnInsert", orderDoc));

        log.info("Persisted order {} with fulfillment {}", orderId, fulfillmentId);
        return Map.of("order_id", orderId, "fulfillment_id", fulfillmentId, "status", "accepted");
    }
}
