package com.accenture.ordersystem.mongodb.steward.functions;

import com.accenture.ordersystem.mongodb.common.Db;
import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.TypedLambdaFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * v1.milestone.record — records ONE SoR milestone as an idempotent fact, then derives
 * where the accumulated set now stands.
 *
 * The three milestones arrive on three independent topics with no ordering between them.
 * They form an unordered SET: any fail → FAILED; all three pass → SETTLED; else waiting.
 *
 * Idempotency: $ifNull in the pipeline update preserves the FIRST write of any milestone.
 *
 * Input : {fulfillment_id, milestone, outcome, reason}
 * Output: {progress: settled|failed|waiting, to_state, reason, passed, recorded}
 */
@PreLoad(route = "v1.milestone.record", instances = 10)
public class MilestoneRecord implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(MilestoneRecord.class);

    private static final Set<String> MILESTONES = Set.of("validated", "preprocessed", "processed");
    private static final int REQUIRED = MILESTONES.size();

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers,
                                           Map<String, Object> input, int instance) throws Exception {
        String fulfillmentId = (String) input.get("fulfillment_id");
        String milestone = (String) input.get("milestone");
        String outcome = (String) input.get("outcome");
        Object reason = input.get("reason");

        if (fulfillmentId == null) throw new IllegalArgumentException("Missing fulfillment_id");
        if (milestone == null || !MILESTONES.contains(milestone)) {
            throw new IllegalArgumentException("Unknown milestone: " + milestone);
        }
        if (!"pass".equals(outcome) && !"fail".equals(outcome)) {
            throw new IllegalArgumentException("outcome must be pass or fail, got: " + outcome);
        }

        Db db = Db.using(headers, instance);

        // $ifNull preserves the first write — a re-delivered milestone is a no-op
        Map<String, Object> milestoneDoc = new HashMap<>();
        milestoneDoc.put("outcome", outcome);
        milestoneDoc.put("reason", reason == null ? "" : String.valueOf(reason));

        String milestonePath = "milestones." + milestone;
        List<Map<String, Object>> pipeline = new ArrayList<>();
        pipeline.add(Map.of("$set", Map.of(
                milestonePath, Map.of("$ifNull", List.of("$" + milestonePath, milestoneDoc)),
                "updated_at", Map.of("$toString", "$$NOW"))));

        Optional<Map<String, Object>> docOpt = db.findOneAndUpdate(
                "fulfillments",
                Map.of("fulfillment_id", fulfillmentId),
                pipeline, true, false, null);

        if (docOpt.isEmpty()) {
            throw new IllegalStateException("Fulfillment not found: " + fulfillmentId);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> milestones = (Map<String, Object>) docOpt.get().getOrDefault("milestones", Map.of());

        long passed = 0, failed = 0;
        String failedAt = null;
        String failedReason = null;

        for (Map.Entry<String, Object> e : milestones.entrySet()) {
            if (e.getValue() instanceof Map<?, ?> m) {
                String oc = (String) m.get("outcome");
                if ("pass".equals(oc)) passed++;
                else if ("fail".equals(oc)) {
                    failed++;
                    if (failedAt == null) {
                        failedAt = e.getKey();
                        failedReason = (String) m.get("reason");
                    }
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("passed", passed);
        result.put("recorded", passed + failed);

        if (failed > 0) {
            result.put("progress", "failed");
            result.put("to_state", "FAILED");
            result.put("reason", "sor_" + failedAt);
            result.put("detail", failedReason);
        } else if (passed >= REQUIRED) {
            result.put("progress", "settled");
            result.put("to_state", "SETTLED");
            result.put("reason", "");
        } else {
            result.put("progress", "waiting");
            result.put("to_state", "NOOP");
            result.put("reason", "");
        }

        log.info("Milestone {}={} for {} — {} of {} in, progress={}",
                milestone, outcome, fulfillmentId, passed, REQUIRED, result.get("progress"));
        return result;
    }
}
