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
 * v1.sor.mock.emit — manually emit a single SoR milestone outcome.
 * Used in DEMO MODE via POST /api/admin/sor/emit/{sor_reference}/{milestone}/{outcome}
 */
@PreLoad(route = "v1.sor.mock.emit", instances = 5)
public class SorMockEmit implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(SorMockEmit.class);

    private static final Map<String, String> MILESTONE_TO_TOPIC = Map.of(
            "validated",    Events.SOR_VALIDATION,
            "preprocessed", Events.SOR_PREPROCESS,
            "processed",    Events.SOR_PROCESS
    );

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers,
                                           Map<String, Object> input, int instance) throws Exception {
        String sorReference = (String) input.get("sor_reference");
        String milestone = (String) input.get("milestone");
        String outcome = (String) input.get("outcome");
        String reason = (String) input.get("reason");

        if (sorReference == null) throw new IllegalArgumentException("Missing sor_reference");
        String topic = MILESTONE_TO_TOPIC.get(milestone);
        if (topic == null) throw new IllegalArgumentException("Unknown milestone: " + milestone);
        if (!"pass".equals(outcome) && !"fail".equals(outcome)) {
            throw new IllegalArgumentException("outcome must be pass or fail");
        }

        // sorReference = "SOR-{fulfillment_id}" — strip the prefix to get the business CID
        String fulfillmentId = sorReference.startsWith("SOR-") ? sorReference.substring(4) : sorReference;

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
        return Map.of("sor_reference", sorReference, "milestone", milestone, "outcome", outcome, "topic", topic);
    }
}
