package com.accenture.ordersystem.steward.functions;

import com.accenture.ordersystem.common.Db;
import com.accenture.ordersystem.common.Demo;
import com.accenture.ordersystem.common.Events;
import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.TypedLambdaFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * v1.scheduled.dispatch - the dispatch-window tick.
 *
 * It only decides WHICH fulfillments have reached their window and emits one tick each;
 * steward-tick-flow does the work. The graph can only be driven from inside an Event Script flow
 * (graph.executor resolves a live flow instance), so the tick rides Kafka rather than an HTTP call
 * back into this JVM.
 *
 * No outbox here on purpose: a tick records no fact. If one is lost, this query finds the
 * fulfillment again on the next run - the query IS the recovery mechanism.
 *
 * Input : {} (scheduler-triggered)
 * Output: {ticked: N}
 */
@PreLoad(route = "v1.scheduled.dispatch", instances = 5)
public class ScheduledDispatch implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(ScheduledDispatch.class);

    private static final String DUE =
            "SELECT fulfillment_id FROM fulfillment WHERE status = 'SCHEDULED' ORDER BY created_at LIMIT 20";

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers,
                                           Map<String, Object> input,
                                           int instance) throws Exception {
        if (Demo.suppressed(input)) {
            Map<String, Object> skipped = new HashMap<>();
            skipped.put("ticked", 0);
            skipped.put("skipped", "demo mode - trigger this step through its /api/v1/admin endpoint");
            return skipped;
        }

        Db db = Db.using(headers, instance);
        List<Map<String, Object>> due = db.query(DUE);

        int ticked = 0;
        for (Map<String, Object> row : due) {
            String fulfillmentId = (String) row.get("fulfillment_id");
            Events.publish(db.postOffice(), "fulfillment.tick", fulfillmentId,
                    Map.of("fulfillment_id", fulfillmentId));
            ticked++;
        }
        if (ticked > 0) {
            log.info("Dispatch window: {} fulfillment(s) ticked", ticked);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("ticked", ticked);
        return result;
    }
}
