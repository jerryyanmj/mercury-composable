package com.accenture.ordersystem.steward.functions;

import com.accenture.ordersystem.common.Db;
import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.TypedLambdaFunction;
import org.platformlambda.core.serializers.SimpleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * v1.fulfillment.create - the birth of a fulfillment.
 *
 * The aggregate row, its first history event (seq 1) and the outbox event announcing it all commit
 * together. The aggregate MUST be inserted before the event: fulfillment_event carries a foreign
 * key to it.
 *
 * Both inserts are ON CONFLICT DO NOTHING, so a redelivered fulfillment.request is a no-op.
 *
 * Input : {fulfillment_id, order_id, route, payload}
 * Output: {fulfillment_id, status}
 */
@PreLoad(route = "v1.fulfillment.create", instances = 10)
public class FulfillmentCreate implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(FulfillmentCreate.class);

    private static final String CREATE_AGGREGATE =
            "INSERT INTO fulfillment(fulfillment_id, order_id, payload, status, created_at, updated_at) " +
            "VALUES (:fulfillment_id, :order_id, :payload::jsonb, 'VALIDATED', now(), now()) " +
            "ON CONFLICT (fulfillment_id) DO NOTHING";
    private static final String BIRTH_EVENT =
            "INSERT INTO fulfillment_event(fulfillment_id, seq, kind, name, outcome, " +
            "from_state, to_state, source, detail, occurred_at) " +
            "VALUES (:fulfillment_id, 1, 'STATE', 'VALIDATED', 'pass', null, 'VALIDATED', " +
            "'STEWARD', '{}', now()) ON CONFLICT DO NOTHING";
    private static final String STAGE_EVENT =
            "INSERT INTO outbox(aggregate_id, topic, payload, headers, created_at) " +
            "VALUES (:aggregate_id, 'fulfillment.status', :payload::jsonb, '{}'::jsonb, now())";

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers,
                                           Map<String, Object> input,
                                           int instance) throws Exception {
        String fulfillmentId = (String) input.get("fulfillment_id");
        String orderId = (String) input.get("order_id");
        Object payload = input.get("payload");

        if (fulfillmentId == null) throw new IllegalArgumentException("Missing fulfillment_id");
        if (orderId == null)       throw new IllegalArgumentException("Missing order_id");
        // Identity conventions are checked at this boundary too: the steward keys its whole
        // lifecycle (and the Redis suspension record) on these ids, so a malformed one must not
        // start a workflow. A rejection here dead-letters the record rather than corrupting state.
        if (!fulfillmentId.startsWith("F")) {
            throw new IllegalArgumentException("fulfillment_id must start with 'F' - got: " + fulfillmentId);
        }
        if (!orderId.startsWith("O")) {
            throw new IllegalArgumentException("order_id must start with 'O' - got: " + orderId);
        }

        Map<String, Object> statusEvent = new HashMap<>();
        statusEvent.put("fulfillment_id", fulfillmentId);
        statusEvent.put("order_id", orderId);
        statusEvent.put("seq", 1);
        statusEvent.put("from_state", null);
        statusEvent.put("to_state", "VALIDATED");
        statusEvent.put("source", "STEWARD");
        statusEvent.put("occurred_at", Instant.now().toString());
        statusEvent.put("reason", null);

        Db.using(headers, instance).transaction()
                .add(CREATE_AGGREGATE, Map.of("fulfillment_id", fulfillmentId,
                        "order_id", orderId, "payload", toJson(payload)))
                .add(BIRTH_EVENT, Map.of("fulfillment_id", fulfillmentId))
                .add(STAGE_EVENT, Map.of("aggregate_id", fulfillmentId, "payload", toJson(statusEvent)))
                .execute();

        log.info("Fulfillment {} created with status VALIDATED", fulfillmentId);

        Map<String, Object> result = new HashMap<>();
        result.put("fulfillment_id", fulfillmentId);
        result.put("status", "VALIDATED");
        return result;
    }

    private static String toJson(Object value) {
        if (value == null) return "{}";
        if (value instanceof String s) return s;
        return SimpleMapper.getInstance().getMapper().writeValueAsString(value);
    }
}
