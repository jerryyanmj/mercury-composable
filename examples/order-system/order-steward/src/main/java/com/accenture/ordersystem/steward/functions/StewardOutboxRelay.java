package com.accenture.ordersystem.steward.functions;

import com.accenture.ordersystem.common.Db;
import com.accenture.ordersystem.common.Demo;
import com.accenture.ordersystem.common.Events;
import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.TypedLambdaFunction;
import org.platformlambda.core.serializers.SimpleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * v1.steward.outbox.relay - publishes staged outbox rows to Kafka and marks them sent.
 *
 * The relay is destination-agnostic: the row's {@code topic} column IS the destination, so a new
 * destination needs no change here. A row is marked sent only after its publish succeeds, which
 * is what keeps the outbox guarantee intact (see {@link Events}).
 *
 * Input : {} (scheduler-triggered)
 * Output: {relayed: N}
 */
@PreLoad(route = "v1.steward.outbox.relay", instances = 5)
public class StewardOutboxRelay implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(StewardOutboxRelay.class);

    private static final String UNSENT =
            "SELECT id, aggregate_id, topic, payload::text AS payload FROM outbox " +
            "WHERE sent_at IS NULL ORDER BY id LIMIT 50";
    private static final String MARK_SENT =
            "UPDATE outbox SET sent_at = now() WHERE id = :id";

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers,
                                           Map<String, Object> input,
                                           int instance) throws Exception {
        if (Demo.suppressed(input)) {
            Map<String, Object> skipped = new HashMap<>();
            skipped.put("relayed", 0);
            skipped.put("skipped", "demo mode - trigger this step through its /api/v1/admin endpoint");
            return skipped;
        }

        Db db = Db.using(headers, instance);
        List<Map<String, Object>> rows = db.query(UNSENT);

        List<Long> sent = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            try {
                Events.publish(db.postOffice(),
                        (String) row.get("topic"),
                        (String) row.get("aggregate_id"),
                        asMap(row.get("payload")));
                if (row.get("id") instanceof Number id) {
                    sent.add(id.longValue());
                }
            } catch (Exception e) {
                // Left unsent on purpose: the next pass retries it.
                log.warn("Failed to relay outbox row {}: {}", row.get("id"), e.getMessage());
            }
        }
        if (!sent.isEmpty()) {
            Db.Transaction txn = db.transaction();
            for (Long id : sent) {
                txn.add(MARK_SENT, Map.of("id", id));
            }
            txn.execute();
            log.info("steward outbox relay: {} message(s) published", sent.size());
        }
        Map<String, Object> result = new HashMap<>();
        result.put("relayed", sent.size());
        return result;
    }

    /** A jsonb column read as ::text arrives as a JSON string; the event body must be the object. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object payload) {
        if (payload instanceof Map) {
            return (Map<String, Object>) payload;
        }
        if (payload == null) {
            return new HashMap<>();
        }
        return SimpleMapper.getInstance().getMapper().readValue(String.valueOf(payload), Map.class);
    }
}
