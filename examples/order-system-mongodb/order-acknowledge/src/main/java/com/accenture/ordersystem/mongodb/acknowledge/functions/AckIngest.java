package com.accenture.ordersystem.mongodb.acknowledge.functions;

import com.accenture.ordersystem.mongodb.common.Events;
import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.EventEnvelope;
import org.platformlambda.core.models.TypedLambdaFunction;
import org.platformlambda.core.system.PostOffice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * v1.ack.ingest — translates a SoR outcome into a fulfillment.ack milestone event.
 *
 * The sor_reference has the form "SOR-{fulfillment_id}", so fulfillment_id is the
 * suffix after stripping the prefix. The milestone name comes from the flow's
 * header.milestone injection (each SoR topic maps to exactly one milestone).
 */
@PreLoad(route = "v1.ack.ingest", instances = 10)
public class AckIngest implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(AckIngest.class);

    private static final String SOR_PREFIX = "SOR-";
    private static final Set<String> VALID_MILESTONES = Set.of("validated", "preprocessed", "processed");

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers,
                                           Map<String, Object> input, int instance) throws Exception {
        String sorReference = (String) input.get("sor_reference");
        String milestone = headers.get("milestone");
        String outcome = (String) input.get("outcome");
        String reason = (String) input.get("reason");

        if (sorReference == null || !sorReference.startsWith(SOR_PREFIX)) {
            throw new IllegalArgumentException("Missing or invalid sor_reference: " + sorReference);
        }
        if (milestone == null || !VALID_MILESTONES.contains(milestone)) {
            throw new IllegalArgumentException("Unknown milestone: " + milestone);
        }
        if (!"pass".equals(outcome) && !"fail".equals(outcome)) {
            throw new IllegalArgumentException("outcome must be pass or fail, got: " + outcome);
        }

        String fulfillmentId = sorReference.substring(SOR_PREFIX.length());

        Map<String, Object> ackPayload = new HashMap<>();
        ackPayload.put("fulfillment_id", fulfillmentId);
        ackPayload.put("milestone", milestone);
        ackPayload.put("outcome", outcome);
        ackPayload.put("reason", reason != null ? reason : "");

        PostOffice po = new PostOffice(headers, instance);
        EventEnvelope notification = new EventEnvelope()
                .setTo("simple.kafka.notification")
                .setHeader("topic", Events.FULFILLMENT_ACK)
                .setHeader("cid", fulfillmentId)
                .setBody(ackPayload);
        po.request(notification, 10_000).get();

        log.info("Ack {} milestone={} outcome={} for fulfillment {}",
                sorReference, milestone, outcome, fulfillmentId);
        return ackPayload;
    }
}
