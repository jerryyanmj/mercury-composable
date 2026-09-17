package com.accenture.ordersystem.mongodb.manager.functions;

import com.accenture.ordersystem.mongodb.common.Db;
import com.accenture.ordersystem.mongodb.common.Events;
import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.TypedLambdaFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * v1.order.aggregate — projects a fulfillment.status event into the CQRS read model.
 *
 * Step 1: update the matching line's status and append a history entry.
 * Step 2: derive the new order_status from all line statuses.
 * Step 3: if the order_status changed, update it and stage an order.status.external outbox entry.
 */
@PreLoad(route = "v1.order.aggregate", instances = 10)
public class OrderAggregate implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(OrderAggregate.class);

    private static final Set<String> TERMINAL = Set.of("SETTLED", "FAILED", "REJECTED");

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers,
                                           Map<String, Object> input, int instance) throws Exception {
        String fulfillmentId = (String) input.get("fulfillment_id");
        String orderId = (String) input.get("order_id");
        String toState = (String) input.get("to_state");
        String fromState = (String) input.get("from_state");
        String source = (String) input.get("source");
        String reason = (String) input.get("reason");
        Number seqNum = (Number) input.get("seq");
        int seq = seqNum != null ? seqNum.intValue() : 0;
        String occurredAt = (String) input.get("occurred_at");
        if (occurredAt == null) occurredAt = Instant.now().toString();

        if (fulfillmentId == null || orderId == null || toState == null) {
            throw new IllegalArgumentException("Missing fulfillment_id, order_id, or to_state");
        }

        Db db = Db.using(headers, instance);
        String now = Instant.now().toString();

        // Step 1: update line status + append history entry (pipeline update for atomicity)
        Map<String, Object> lineEntry = new HashMap<>();
        lineEntry.put("seq", seq);
        lineEntry.put("fulfillment_id", fulfillmentId);
        lineEntry.put("from_state", fromState);
        lineEntry.put("to_state", toState);
        lineEntry.put("source", source);
        lineEntry.put("occurred_at", occurredAt);
        lineEntry.put("reason", reason);

        List<Map<String, Object>> pipeline = new ArrayList<>();

        // Update matching line status
        Map<String, Object> mapLines = new HashMap<>();
        mapLines.put("input", "$lines");
        mapLines.put("as", "l");
        Map<String, Object> condExpr = new HashMap<>();
        condExpr.put("if", Map.of("$eq", List.of("$$l.fulfillment_id", fulfillmentId)));
        condExpr.put("then", Map.of("$mergeObjects", List.of("$$l", Map.of("line_status", toState))));
        condExpr.put("else", "$$l");
        mapLines.put("in", Map.of("$cond", condExpr));
        pipeline.add(Map.of("$set", Map.of("lines", Map.of("$map", mapLines))));

        // Append history entry (idempotent: only if seq not already present)
        Map<String, Object> seqInHistory = Map.of("$in", List.of(seq,
                Map.of("$map", Map.of("input", "$history", "as", "h", "in", "$$h.seq"))));
        Map<String, Object> appendHistory = Map.of("$cond", Map.of(
                "if", seqInHistory,
                "then", "$history",
                "else", Map.of("$concatArrays", List.of("$history", List.of(lineEntry)))));
        pipeline.add(Map.of("$set", Map.of("history", appendHistory, "updated_at", now)));

        Map<String, Object> orderFilter = Map.of("order_id", orderId);
        Optional<Map<String, Object>> afterOpt = db.findOneAndUpdate(
                "orders", orderFilter, pipeline, true, false, null);

        if (afterOpt.isEmpty()) {
            log.warn("Order {} not found for aggregate", orderId);
            return Map.of("order_id", orderId, "skipped", true);
        }

        Map<String, Object> after = afterOpt.get();

        // Step 2: derive new order_status from all line statuses
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) after.get("lines");
        String derivedOrderStatus = deriveOrderStatus(lines);
        String currentOrderStatus = (String) after.get("order_status");

        if (derivedOrderStatus == null || derivedOrderStatus.equals(currentOrderStatus)) {
            return Map.of("order_id", orderId, "fulfillment_id", fulfillmentId,
                    "order_status", currentOrderStatus != null ? currentOrderStatus : "PENDING");
        }

        // Step 3: update order_status and stage external notification
        String outboxId = UUID.randomUUID().toString();
        Map<String, Object> extPayload = new HashMap<>();
        extPayload.put("order_id", orderId);
        extPayload.put("order_status", derivedOrderStatus);
        extPayload.put("occurred_at", now);

        Map<String, Object> outboxEntry = new HashMap<>();
        outboxEntry.put("id", outboxId);
        outboxEntry.put("topic", Events.ORDER_STATUS_EXTERNAL);
        outboxEntry.put("payload", extPayload);
        outboxEntry.put("sent_at", null);

        // Guard: only update if still in the old status (prevents double-write)
        Map<String, Object> statusGuard = new HashMap<>();
        statusGuard.put("order_id", orderId);
        statusGuard.put("order_status", Map.of("$ne", derivedOrderStatus));

        Map<String, Object> statusUpdate = new HashMap<>();
        statusUpdate.put("$set", Map.of("order_status", derivedOrderStatus, "updated_at", now));
        statusUpdate.put("$inc", Map.of("status_version", 1));
        statusUpdate.put("$push", Map.of("outbox", outboxEntry));

        db.upsertOne("orders", statusGuard,
                Map.of("$set", Map.of("order_status", derivedOrderStatus,
                        "updated_at", now),
                        "$inc", Map.of("status_version", 1),
                        "$push", Map.of("outbox", outboxEntry)));

        log.info("Order {} concluded: {} -> {}", orderId, currentOrderStatus, derivedOrderStatus);
        return Map.of("order_id", orderId, "fulfillment_id", fulfillmentId,
                "order_status", derivedOrderStatus);
    }

    @SuppressWarnings("unchecked")
    private String deriveOrderStatus(List<Map<String, Object>> lines) {
        if (lines == null || lines.isEmpty()) return null;
        boolean allTerminal = lines.stream().allMatch(l -> TERMINAL.contains(l.get("line_status")));
        if (!allTerminal) return null;
        boolean allSettled = lines.stream().allMatch(l -> "SETTLED".equals(l.get("line_status")));
        if (allSettled) return "COMPLETED";
        boolean anySettled = lines.stream().anyMatch(l -> "SETTLED".equals(l.get("line_status")));
        return anySettled ? "PARTIALLY_FAILED" : "FAILED";
    }
}
