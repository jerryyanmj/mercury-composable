package com.accenture.ordersystem.mongodb.external.functions;

import com.accenture.ordersystem.mongodb.common.Events;
import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.EventEnvelope;
import org.platformlambda.core.models.TypedLambdaFunction;
import org.platformlambda.core.system.PostOffice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * v1.sor.milestone.submit — SQL-compatible SoR milestone endpoint.
 * Used via POST /api/v1/sor/{milestone} with body {fulfillment_id, outcome}.
 * Matches the order-system (Postgres) mock interface so the same Postman collection works for both.
 */
@PreLoad(route = "v1.sor.milestone.submit", instances = 5)
public class SorMilestoneSubmit implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(SorMilestoneSubmit.class);

    private static final Map<String, String> MILESTONE_TO_TOPIC = Map.of(
            "validated",    Events.SOR_VALIDATION,
            "preprocessed", Events.SOR_PREPROCESS,
            "processed",    Events.SOR_PROCESS
    );

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers,
                                           Map<String, Object> input, int instance) throws Exception {
        String fulfillmentId = (String) input.get("fulfillment_id");
        String milestone = (String) input.get("milestone");
        String outcome = (String) input.get("outcome");
        String reason = (String) input.get("reason");

        if (fulfillmentId == null) throw new IllegalArgumentException("Missing fulfillment_id");
        if (milestone == null) throw new IllegalArgumentException("Missing milestone");
        if (outcome == null) throw new IllegalArgumentException("Missing outcome");

        String topic = MILESTONE_TO_TOPIC.get(milestone);
        if (topic == null) throw new IllegalArgumentException("Unknown milestone: " + milestone);
        if (!"pass".equals(outcome) && !"fail".equals(outcome)) {
            throw new IllegalArgumentException("outcome must be pass or fail");
        }

        String sorReference = "SOR-" + fulfillmentId;

        Map<String, Object> payload = new HashMap<>();
        payload.put("sor_reference", sorReference);
        payload.put("outcome", outcome);
        payload.put("reason", reason != null ? reason : "");

        PostOffice po = new PostOffice(headers, instance);
        EventEnvelope notification = new EventEnvelope()
                .setTo("simple.kafka.notification")
                .setHeader("topic", topic)
                .setHeader("cid", fulfillmentId)
                .setBody(payload);
        po.request(notification, 10_000).get();

        log.info("Emitted {} for {} with outcome={}", milestone, sorReference, outcome);
        return Map.of("topic", topic, "published", true);
    }
}
