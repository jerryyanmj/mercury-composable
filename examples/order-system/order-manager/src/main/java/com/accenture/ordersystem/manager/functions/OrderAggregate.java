package com.accenture.ordersystem.manager.functions;

import com.accenture.ordersystem.common.Db;
import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.TypedLambdaFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * v1.order.aggregate - projects a fulfillment.status event into the CQRS read model.
 *
 * The manager never queries the steward; this timeline is rebuilt purely from events. A
 * redelivered event is absorbed by line_history's unique (fulfillment_id, seq) - that natural key
 * is why the system needs no inbox table.
 *
 * Input : {fulfillment_id, order_id, seq, from_state, to_state, source, occurred_at, reason}
 * Output: {updated, fulfillment_id, order_id, to_state, reason, order_status, status_version}
 *          - the flow branches on to_state and forwards the rest externally
 */
@PreLoad(route = "v1.order.aggregate", instances = 10)
public class OrderAggregate implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(OrderAggregate.class);

    private static final String APPEND_HISTORY =
            "INSERT INTO line_history(fulfillment_id, seq, from_state, to_state, changed_at, reason, detail) " +
            "VALUES (:fulfillment_id, :seq, :from_state, :to_state, " +
            "COALESCE(:occurred_at::timestamptz, now()), :reason, '{}') " +
            "ON CONFLICT (fulfillment_id, seq) DO NOTHING";
    private static final String UPDATE_LINE =
            "UPDATE routing_map SET line_status = :to_state WHERE fulfillment_id = :fulfillment_id";
    /*
     * Conclude the order: roll it up AND stage its external notification, in one statement.
     *
     * This is the piece that was missing. Every other state change in this system writes its event
     * to the outbox inside the same transaction; the external order status was published directly
     * from the flow instead, so it had no outbox guarantee and the manager's relay had nothing to
     * publish. Now the conclusion and the announcement of it commit together, or neither does.
     *
     * Two subtleties:
     *
     * 1. The current line's status is OVERLAID from :to_state rather than read from routing_map.
     *    Postgres evaluates every CTE against the same snapshot, so a sibling statement's write to
     *    routing_map is not visible here - reading it would see the line as still in flight and
     *    silently skip the rollup.
     *
     * 2. The INSERT selects from the UPDATE's RETURNING, so an outbox row exists only when the
     *    order status ACTUALLY CHANGED (the UPDATE's WHERE requires it). A terminal event that
     *    leaves the order alone - because a sibling line is still in flight, or because it is a
     *    redelivery - produces no notification and no version bump.
     */
    private static final String CONCLUDE_ORDER = """
            WITH ln AS (
                SELECT r.fulfillment_id,
                       CASE WHEN r.fulfillment_id = :fulfillment_id THEN :to_state
                            ELSE r.line_status END AS status
                  FROM routing_map r
                 WHERE r.order_id = (SELECT order_id FROM routing_map
                                      WHERE fulfillment_id = :fulfillment_id)
            ), flags AS (
                SELECT fulfillment_id, status,
                       COALESCE(status IN ('SETTLED','REJECTED')
                                OR status LIKE 'FAILED%', false) AS terminal,
                       COALESCE(status = 'SETTLED', false)        AS settled
                  FROM ln
            ), rolled AS (
                SELECT CASE
                         WHEN NOT bool_and(terminal) THEN NULL
                         WHEN bool_and(settled)      THEN 'COMPLETED'
                         WHEN bool_or(settled)       THEN 'PARTIALLY_FAILED'
                         ELSE 'FAILED'
                       END AS status,
                       jsonb_agg(jsonb_build_object('fulfillment_id', fulfillment_id,
                                                    'status', status)
                                 ORDER BY fulfillment_id) AS lines
                  FROM flags
            ), advanced AS (
                UPDATE "order" o
                   SET order_status   = r.status,
                       status_version = o.status_version + 1,
                       updated_at     = now()
                  FROM rolled r
                 WHERE o.order_id = (SELECT order_id FROM routing_map
                                      WHERE fulfillment_id = :fulfillment_id)
                   AND r.status IS NOT NULL
                   AND o.order_status <> r.status
                RETURNING o.order_id, o.external_party, o.order_status, o.status_version, r.lines
            )
            INSERT INTO outbox(aggregate_id, topic, payload, headers, created_at)
            SELECT a.order_id, 'order.status.external',
                   jsonb_build_object('order_id',       a.order_id,
                                      'external_party', a.external_party,
                                      'status',         a.order_status,
                                      'version',        a.status_version,
                                      'lines',          a.lines,
                                      'reason',         :reason::text,
                                      'at',             :occurred_at::text),
                   '{}'::jsonb, now()
              FROM advanced a
            """;

    /** Read back the aggregate so the flow can forward the authoritative version externally. */
    private static final String READ_ORDER =
            "SELECT o.order_id, o.order_status, o.status_version FROM \"order\" o " +
            "JOIN routing_map r ON r.order_id = o.order_id WHERE r.fulfillment_id = :fulfillment_id";

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers,
                                           Map<String, Object> input,
                                           int instance) throws Exception {
        String fulfillmentId = (String) input.get("fulfillment_id");
        String fromState = (String) input.get("from_state");
        String toState = (String) input.get("to_state");
        String reason = (String) input.get("reason");
        String occurredAt = (String) input.get("occurred_at");
        Object seq = input.get("seq");

        if (fulfillmentId == null) throw new IllegalArgumentException("Missing fulfillment_id");
        if (toState == null)       throw new IllegalArgumentException("Missing to_state");

        Map<String, Object> historyParams = new HashMap<>();
        historyParams.put("fulfillment_id", fulfillmentId);
        historyParams.put("seq", seq instanceof Number n ? n.intValue() : 0);
        historyParams.put("from_state", fromState);
        historyParams.put("to_state", toState);
        historyParams.put("occurred_at", occurredAt);
        historyParams.put("reason", reason);

        Db db = Db.using(headers, instance);
        Db.Transaction txn = db.transaction()
                .add(APPEND_HISTORY, historyParams)
                .add(UPDATE_LINE, Map.of("to_state", toState, "fulfillment_id", fulfillmentId));

        if (isTerminal(toState)) {
            Map<String, Object> conclude = new HashMap<>();
            conclude.put("fulfillment_id", fulfillmentId);
            conclude.put("to_state", toState);
            conclude.put("reason", reason == null ? "" : reason);
            conclude.put("occurred_at", occurredAt == null ? Instant.now().toString() : occurredAt);
            txn.add(CONCLUDE_ORDER, conclude);
        }
        txn.execute();

        log.info("Aggregated status {}->{} for fulfillment {}", fromState, toState, fulfillmentId);

        // Read the aggregate AFTER the commit: status_version is the external dedupe key, so the
        // notification must carry the value that actually landed, not one computed before the write.
        Map<String, Object> order = db.queryOne(READ_ORDER, Map.of("fulfillment_id", fulfillmentId))
                .orElseGet(HashMap::new);

        // The flow decides what happens next from these fields: f:includes over to_state drives
        // the terminal decision, and notify-external maps the rest onto order.status.external.
        Map<String, Object> result = new HashMap<>();
        result.put("updated", true);
        result.put("fulfillment_id", fulfillmentId);
        result.put("order_id", order.getOrDefault("order_id", input.get("order_id")));
        result.put("to_state", toState);
        result.put("reason", reason == null ? "" : reason);
        result.put("order_status", order.get("order_status"));
        result.put("status_version", order.get("status_version"));
        return result;
    }

    private boolean isTerminal(String state) {
        return "SETTLED".equals(state) || "FAILED".equals(state) || "REJECTED".equals(state);
    }
}
