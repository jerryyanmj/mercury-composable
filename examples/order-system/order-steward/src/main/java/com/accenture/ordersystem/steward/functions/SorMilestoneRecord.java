package com.accenture.ordersystem.steward.functions;

import com.accenture.ordersystem.common.Db;
import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.TypedLambdaFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * v1.sor.milestone.record - record ONE SoR milestone as a fact, then derive where the fulfillment
 * now stands.
 *
 * The three milestones arrive on three independent topics with no ordering between them, so they
 * are NOT a sequence of states - they are a SET of facts. Modelling them as sequential states
 * (SOR_VALIDATED -> SOR_PREPROCESSED -> ...) invented an order that does not exist, which is what
 * let a late-arriving failure be attributed to the wrong stage.
 *
 * So each milestone is stored as kind='SOR_MILESTONE' with a name and an outcome and NO
 * from_state/to_state - it is not a transition. The schema anticipated exactly this: a partial
 * unique index on (fulfillment_id, name) makes a replayed milestone a no-op insert, which is what
 * makes arrival order irrelevant.
 *
 * The fulfillment's terminal state is then DERIVED from the accumulated set, never from arrival
 * order:  any fail -> FAILED (named by the milestone that failed);  all three pass -> SETTLED;
 * otherwise still waiting, and the fulfillment stays DISPATCHED.
 *
 * Input : {fulfillment_id, milestone, outcome, reason}
 * Output: {progress: settled|failed|waiting, to_state, reason, passed, recorded}
 */
@PreLoad(route = "v1.sor.milestone.record", instances = 10)
public class SorMilestoneRecord implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(SorMilestoneRecord.class);

    private static final Set<String> MILESTONES = Set.of("validated", "preprocessed", "processed");
    private static final int REQUIRED = MILESTONES.size();

    /*
     * ON CONFLICT names the partial index's predicate so Postgres can infer it. This is the whole
     * idempotency story: the same milestone delivered twice, or delivered late, is simply not
     * inserted a second time.
     */
    private static final String RECORD_MILESTONE = """
            INSERT INTO fulfillment_event(fulfillment_id, seq, kind, name, outcome,
                                          from_state, to_state, source, detail, occurred_at)
            VALUES (:fulfillment_id,
                    (SELECT COALESCE(MAX(seq), 0) + 1 FROM fulfillment_event
                      WHERE fulfillment_id = :fulfillment_id),
                    'SOR_MILESTONE', :milestone, :outcome, NULL, NULL, 'SOR',
                    jsonb_build_object('reason', :reason::text), now())
            ON CONFLICT (fulfillment_id, name) WHERE kind = 'SOR_MILESTONE' DO NOTHING
            """;

    private static final String PROGRESS = """
            SELECT count(*) FILTER (WHERE outcome = 'pass')            AS passed,
                   count(*) FILTER (WHERE outcome = 'fail')            AS failed,
                   min(name) FILTER (WHERE outcome = 'fail')           AS failed_at,
                   min(detail->>'reason') FILTER (WHERE outcome = 'fail') AS failed_reason
              FROM fulfillment_event
             WHERE fulfillment_id = :fulfillment_id AND kind = 'SOR_MILESTONE'
            """;

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers,
                                           Map<String, Object> input,
                                           int instance) throws Exception {
        String fulfillmentId = (String) input.get("fulfillment_id");
        String milestone = (String) input.get("milestone");
        String outcome = (String) input.get("outcome");
        Object reason = input.get("reason");

        if (fulfillmentId == null) throw new IllegalArgumentException("Missing fulfillment_id");
        if (milestone == null || !MILESTONES.contains(milestone)) {
            throw new IllegalArgumentException("Unknown milestone: " + milestone);
        }
        if (!"pass".equals(outcome) && !"fail".equals(outcome)) {
            throw new IllegalArgumentException("outcome must be pass or fail - got: " + outcome);
        }
        Db db = Db.using(headers, instance);

        Map<String, Object> record = new HashMap<>();
        record.put("fulfillment_id", fulfillmentId);
        record.put("milestone", milestone);
        record.put("outcome", outcome);
        record.put("reason", reason == null ? null : String.valueOf(reason));
        db.transaction().add(RECORD_MILESTONE, record).execute();

        Map<String, Object> counts = db.queryOne(PROGRESS, Map.of("fulfillment_id", fulfillmentId))
                .orElseGet(HashMap::new);
        long passed = asLong(counts.get("passed"));
        long failed = asLong(counts.get("failed"));

        Map<String, Object> result = new HashMap<>();
        result.put("passed", passed);
        result.put("recorded", passed + failed);

        if (failed > 0) {
            // Named by the milestone that actually failed - true regardless of when it arrived.
            String failedAt = String.valueOf(counts.get("failed_at"));
            result.put("progress", "failed");
            result.put("to_state", "FAILED");
            result.put("reason", "sor_" + failedAt);
            result.put("detail", counts.get("failed_reason"));
        } else if (passed >= REQUIRED) {
            result.put("progress", "settled");
            result.put("to_state", "SETTLED");
            result.put("reason", "");
        } else {
            result.put("progress", "waiting");
            result.put("to_state", "NOOP");
            result.put("reason", "");
        }
        log.info("Milestone {}={} for {} - {} of {} in, progress={}",
                milestone, outcome, fulfillmentId, passed, REQUIRED, result.get("progress"));
        return result;
    }

    private static long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }
}
