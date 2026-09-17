package com.accenture.ordersystem.mongodb.steward.functions;

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
import java.util.UUID;

/**
 * v1.steward.record.status — records a fulfillment lifecycle transition.
 *
 * Uses a MongoDB pipeline update to atomically:
 *   - increment _seq
 *   - update lifecycle_state
 *   - append a fulfillment.status outbox entry (using the incremented _seq)
 *
 * The filter {lifecycle_state: {$ne: to_state}} guards against self-transitions.
 * NOOP to_state short-circuits before any DB write.
 */
@PreLoad(route = "v1.steward.record.status", instances = 10)
public class StewardRecordStatus implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(StewardRecordStatus.class);

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers,
                                           Map<String, Object> input, int instance) throws Exception {
        String fulfillmentId = (String) input.get("fulfillment_id");
        String toState = (String) input.get("to_state");
        String source = input.get("source") instanceof String s ? s : "STEWARD";
        String fromState = (String) input.get("from_state");
        String reason = (String) input.get("reason");

        if (fulfillmentId == null) throw new IllegalArgumentException("Missing fulfillment_id");
        if (toState == null)       throw new IllegalArgumentException("Missing to_state");

        if ("NOOP".equals(toState)) {
            log.info("NOOP for fulfillment {} — milestone set incomplete or duplicate ack", fulfillmentId);
            Map<String, Object> noop = new HashMap<>();
            noop.put("fulfillment_id", fulfillmentId);
            noop.put("to_state", "NOOP");
            noop.put("ignored", true);
            return noop;
        }

        Db db = Db.using(headers, instance);
        String now = Instant.now().toString();
        String outboxId = UUID.randomUUID().toString();

        // When the caller doesn't supply from_state, read the current lifecycle_state before the update
        if (fromState == null) {
            Optional<Map<String, Object>> current = db.findOne("fulfillments",
                    Map.of("fulfillment_id", fulfillmentId));
            fromState = current.map(d -> (String) d.get("lifecycle_state")).orElse(null);
        }

        // Aggregate pipeline update: increment _seq, update state, append outbox entry.
        // The outbox payload captures the pre-computed fields from Java plus document field refs.
        Map<String, Object> outboxPayload = new HashMap<>();
        outboxPayload.put("fulfillment_id", "$fulfillment_id");
        outboxPayload.put("order_id", "$order_id");
        outboxPayload.put("seq", Map.of("$add", List.of("$_seq", 1)));
        outboxPayload.put("from_state", fromState);
        outboxPayload.put("to_state", toState);
        outboxPayload.put("source", source);
        outboxPayload.put("occurred_at", now);
        outboxPayload.put("reason", reason);

        Map<String, Object> outboxEntry = new HashMap<>();
        outboxEntry.put("id", outboxId);
        outboxEntry.put("topic", Events.FULFILLMENT_STATUS);
        outboxEntry.put("payload", outboxPayload);
        outboxEntry.put("sent_at", null);

        List<Map<String, Object>> pipeline = new ArrayList<>();
        pipeline.add(Map.of("$set", Map.of(
                "_seq", Map.of("$add", List.of("$_seq", 1)),
                "lifecycle_state", toState,
                "updated_at", now)));
        pipeline.add(Map.of("$set", Map.of(
                "outbox", Map.of("$concatArrays", List.of("$outbox", List.of(outboxEntry))))));

        // Guard against self-transition
        Map<String, Object> filter = new HashMap<>();
        filter.put("fulfillment_id", fulfillmentId);
        filter.put("lifecycle_state", Map.of("$ne", toState));

        Optional<Map<String, Object>> result = db.findOneAndUpdate(
                "fulfillments", filter, pipeline, true, false, null);

        if (result.isEmpty()) {
            log.info("Fulfillment {} is already {} — nothing to record", fulfillmentId, toState);
            Map<String, Object> unchanged = new HashMap<>();
            unchanged.put("fulfillment_id", fulfillmentId);
            unchanged.put("to_state", toState);
            unchanged.put("unchanged", true);
            return unchanged;
        }

        Number seq = (Number) result.get().get("_seq");
        log.info("Recorded {}→{} for fulfillment {} (seq={})", fromState, toState, fulfillmentId, seq);

        Map<String, Object> out = new HashMap<>();
        out.put("fulfillment_id", fulfillmentId);
        out.put("to_state", toState);
        out.put("seq", seq);
        return out;
    }
}
