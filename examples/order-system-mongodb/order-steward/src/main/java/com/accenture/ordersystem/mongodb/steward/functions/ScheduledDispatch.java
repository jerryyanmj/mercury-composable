package com.accenture.ordersystem.mongodb.steward.functions;

import com.accenture.ordersystem.mongodb.common.Db;
import com.accenture.ordersystem.mongodb.common.Demo;
import com.accenture.ordersystem.mongodb.common.Events;
import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.EventEnvelope;
import org.platformlambda.core.models.TypedLambdaFunction;
import org.platformlambda.core.system.PostOffice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * v1.scheduled.dispatch — finds all SCHEDULED fulfillments and emits a fulfillment.tick event
 * for each. Does NOT use the outbox (tick events are ephemeral — if the pod restarts before
 * sending, the next cron tick re-discovers the same SCHEDULED fulfillments).
 */
@PreLoad(route = "v1.scheduled.dispatch", instances = 1)
public class ScheduledDispatch implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(ScheduledDispatch.class);

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers,
                                           Map<String, Object> input, int instance) throws Exception {
        if (Demo.suppressed(headers)) {
            return Map.of("status", "suppressed");
        }

        Db db = Db.using(headers, instance);
        PostOffice po = db.postOffice();

        List<Map<String, Object>> scheduled = db.findMany("fulfillments",
                Map.of("lifecycle_state", "SCHEDULED"), 200);

        int dispatched = 0;
        for (Map<String, Object> doc : scheduled) {
            String fulfillmentId = (String) doc.get("fulfillment_id");
            if (fulfillmentId == null) continue;

            Map<String, Object> tickPayload = new HashMap<>();
            tickPayload.put("fulfillment_id", fulfillmentId);

            EventEnvelope tick = new EventEnvelope()
                    .setTo("simple.kafka.notification")
                    .setHeader("topic", Events.FULFILLMENT_TICK)
                    .setHeader("cid", fulfillmentId)
                    .setBody(tickPayload);
            po.request(tick, 10_000).get();
            dispatched++;
            log.debug("Ticked fulfillment {}", fulfillmentId);
        }

        log.info("Scheduled dispatch: ticked {} fulfillments", dispatched);
        return Map.of("dispatched", dispatched);
    }
}
