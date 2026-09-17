package com.accenture.ordersystem.steward.functions;

import com.accenture.ordersystem.common.Db;
import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.TypedLambdaFunction;
import org.platformlambda.core.serializers.SimpleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * v1.sor.dispatch - assigns the SoR reference and stages the sor.dispatch event, atomically.
 *
 * The lifecycle transition itself is written by v1.txn.record.status, which is the single writer
 * of fulfillment_event.
 *
 * Input : {fulfillment_id}
 * Output: {fulfillment_id, sor_reference}
 */
@PreLoad(route = "v1.sor.dispatch", instances = 10)
public class SorDispatch implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(SorDispatch.class);

    private static final String READ_FULFILLMENT =
            "SELECT order_id, payload::text AS payload FROM fulfillment WHERE fulfillment_id = :fid";
    private static final String ASSIGN_REFERENCE =
            "UPDATE fulfillment SET sor_reference = :sor_reference, scheduled_at = now(), " +
            "updated_at = now() WHERE fulfillment_id = :fulfillment_id";
    private static final String STAGE_DISPATCH =
            "INSERT INTO outbox(aggregate_id, topic, payload, headers, created_at) " +
            "VALUES (:aggregate_id, 'sor.dispatch', :payload::jsonb, '{}'::jsonb, now())";

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers,
                                           Map<String, Object> input,
                                           int instance) throws Exception {
        String fulfillmentId = (String) input.get("fulfillment_id");
        if (fulfillmentId == null) {
            throw new IllegalArgumentException("Missing fulfillment_id");
        }
        Db db = Db.using(headers, instance);

        Map<String, Object> fulfillment = db.queryOneOrFail(READ_FULFILLMENT,
                Map.of("fid", fulfillmentId), "Fulfillment not found: " + fulfillmentId);

        String sorReference = "SOR-" + fulfillmentId;

        Map<String, Object> sorEvent = new HashMap<>();
        sorEvent.put("fulfillment_id", fulfillmentId);
        sorEvent.put("sor_reference", sorReference);
        sorEvent.put("payload", asMap(fulfillment.get("payload")));

        db.transaction()
                .add(ASSIGN_REFERENCE, Map.of("sor_reference", sorReference,
                        "fulfillment_id", fulfillmentId))
                .add(STAGE_DISPATCH, Map.of("aggregate_id", fulfillmentId,
                        "payload", SimpleMapper.getInstance().getMapper().writeValueAsString(sorEvent)))
                .execute();

        log.info("Staged SoR dispatch for fulfillment {} as {}", fulfillmentId, sorReference);

        Map<String, Object> result = new HashMap<>();
        result.put("fulfillment_id", fulfillmentId);
        result.put("sor_reference", sorReference);
        return result;
    }

    /** A jsonb column read as ::text arrives as a JSON string; the event needs the object. */
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
