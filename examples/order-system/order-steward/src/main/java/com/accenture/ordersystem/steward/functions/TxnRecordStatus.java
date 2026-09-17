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
 * v1.txn.record.status - records a fulfillment state transition.
 *
 * This is the SINGLE writer of fulfillment_event, and therefore the single source of the
 * monotonic seq the manager's read model dedupes on. The state change, the history row and the
 * outbox event announcing it all commit together.
 *
 * Input : {fulfillment_id, to_state, source, from_state (optional), reason (optional)}
 * Output: {fulfillment_id, to_state, seq}
 */
@PreLoad(route = "v1.txn.record.status", instances = 10)
public class TxnRecordStatus implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(TxnRecordStatus.class);

    private static final String READ_AGGREGATE =
            "SELECT order_id, status FROM fulfillment WHERE fulfillment_id = :fid";
    private static final String NEXT_SEQ =
            "SELECT COALESCE(MAX(seq), 0) + 1 AS next_seq FROM fulfillment_event WHERE fulfillment_id = :fid";
    private static final String UPDATE_STATUS =
            "UPDATE fulfillment SET status = :to_state, updated_at = now() " +
            "WHERE fulfillment_id = :fulfillment_id";
    private static final String APPEND_EVENT =
            "INSERT INTO fulfillment_event(fulfillment_id, seq, kind, name, outcome, " +
            "from_state, to_state, source, detail, occurred_at) " +
            "VALUES (:fulfillment_id, :seq, 'STATE', :to_state, 'pass', " +
            ":from_state, :to_state, :source, '{}', now())";
    private static final String STAGE_EVENT =
            "INSERT INTO outbox(aggregate_id, topic, payload, headers, created_at) " +
            "VALUES (:aggregate_id, 'fulfillment.status', :payload::jsonb, '{}'::jsonb, now())";

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers,
                                           Map<String, Object> input,
                                           int instance) throws Exception {
        String fulfillmentId = (String) input.get("fulfillment_id");
        String toState = (String) input.get("to_state");
        String reason = (String) input.get("reason");
        String source = input.get("source") == null ? "STEWARD" : (String) input.get("source");
        String fromState = (String) input.get("from_state");

        if (fulfillmentId == null) throw new IllegalArgumentException("Missing fulfillment_id");
        if (toState == null)       throw new IllegalArgumentException("Missing to_state");

        // The lifecycle graph answers NOOP when an event found nothing to resume - a duplicate or
        // stale acknowledgement arriving after the fulfillment already reached a terminal state.
        // Recording it would let a late ack rewrite a completed fulfillment, so stop here.
        if ("NOOP".equals(toState)) {
            log.info("Ignoring a duplicate or stale event for fulfillment {} - nothing to resume",
                    fulfillmentId);
            Map<String, Object> noop = new HashMap<>();
            noop.put("fulfillment_id", fulfillmentId);
            noop.put("to_state", "NOOP");
            noop.put("ignored", true);
            return noop;
        }
        Db db = Db.using(headers, instance);

        // The aggregate row carries both the current status (the implied from_state) and the
        // order_id the manager's read model rolls the line up to, so read it once for both.
        Map<String, Object> aggregate = db.queryOneOrFail(READ_AGGREGATE, Map.of("fid", fulfillmentId),
                "Fulfillment not found: " + fulfillmentId);
        String orderId = (String) aggregate.get("order_id");
        String currentState = (String) aggregate.get("status");
        if (fromState == null || fromState.isBlank()) {
            fromState = currentState;
        }
        // A transition into the state you are already in is not a transition. Milestones arrive
        // independently, so once the set has decided the outcome every LATER ack derives that same
        // outcome again - without this the log filled with FAILED -> FAILED and the manager was sent
        // a duplicate announcement for each one.
        if (toState.equals(currentState)) {
            log.info("Fulfillment {} is already {} - nothing to record", fulfillmentId, toState);
            Map<String, Object> unchanged = new HashMap<>();
            unchanged.put("fulfillment_id", fulfillmentId);
            unchanged.put("to_state", toState);
            unchanged.put("unchanged", true);
            return unchanged;
        }
        int seq = db.queryOne(NEXT_SEQ, Map.of("fid", fulfillmentId))
                .map(row -> ((Number) row.get("next_seq")).intValue())
                .orElse(1);

        Map<String, Object> statusEvent = new HashMap<>();
        statusEvent.put("fulfillment_id", fulfillmentId);
        statusEvent.put("order_id", orderId);
        statusEvent.put("seq", seq);
        statusEvent.put("from_state", fromState);
        statusEvent.put("to_state", toState);
        statusEvent.put("source", source);
        statusEvent.put("occurred_at", Instant.now().toString());
        statusEvent.put("reason", reason);

        Map<String, Object> eventParams = new HashMap<>();
        eventParams.put("fulfillment_id", fulfillmentId);
        eventParams.put("seq", seq);
        eventParams.put("to_state", toState);
        eventParams.put("from_state", fromState);
        eventParams.put("source", source);

        db.transaction()
                .add(UPDATE_STATUS, Map.of("to_state", toState, "fulfillment_id", fulfillmentId))
                .add(APPEND_EVENT, eventParams)
                .add(STAGE_EVENT, Map.of("aggregate_id", fulfillmentId,
                        "payload", SimpleMapper.getInstance().getMapper().writeValueAsString(statusEvent)))
                .execute();

        log.info("Recorded status {}->{} for fulfillment {} (seq={})",
                fromState, toState, fulfillmentId, seq);

        Map<String, Object> result = new HashMap<>();
        result.put("fulfillment_id", fulfillmentId);
        result.put("to_state", toState);
        result.put("seq", seq);
        return result;
    }
}
