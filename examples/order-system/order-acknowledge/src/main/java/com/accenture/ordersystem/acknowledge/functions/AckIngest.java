package com.accenture.ordersystem.acknowledge.functions;

import com.accenture.ordersystem.common.Events;
import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.TypedLambdaFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * v1.ack.ingest - the anti-corruption layer between the System of Record and the steward.
 *
 * The SoR publishes each milestone on its own topic (sor.validation, sor.preprocess,
 * sor.process) with no milestone field in the payload, and identifies work by sor_reference.
 * Three inbound flows bind those topics and stamp the milestone each one implies; this function
 * normalizes whatever arrives into the single internal fulfillment.ack contract, keyed by
 * fulfillment_id.
 *
 * Everything downstream - the steward's graph, the state machine, the fulfillment.ack contract -
 * is unaware the SoR uses three topics. That containment is the point of this component.
 *
 * Delivery to the steward is Kafka, never HTTP: this app is deliberately stateless, so it has
 * nowhere to keep a retry queue. Kafka supplies the durability and decoupling instead, and a
 * redelivered ack is harmless because resume is consume-on-read.
 *
 * Input : {sor_reference, fulfillment_id (optional), milestone, outcome, reason}
 * Output: {forwarded: true, fulfillment_id, milestone}
 */
@PreLoad(route = "v1.ack.ingest", instances = 10)
public class AckIngest implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(AckIngest.class);

    private static final String SOR_REFERENCE_PREFIX = "SOR-";
    private static final Set<String> MILESTONES = Set.of("validated", "preprocessed", "processed");

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers,
                                           Map<String, Object> input,
                                           int instance) throws Exception {
        String milestone = (String) input.get("milestone");
        String outcome = (String) input.get("outcome");
        String reason = (String) input.get("reason");
        String sorReference = (String) input.get("sor_reference");

        // The milestone is supplied by the binding, not the payload - an unknown value means a
        // flow was wired to the wrong topic, which is worth failing loudly rather than forwarding.
        if (milestone == null || !MILESTONES.contains(milestone)) {
            throw new IllegalArgumentException("Unknown milestone: " + milestone);
        }
        if (outcome == null) {
            throw new IllegalArgumentException("Missing outcome");
        }
        String fulfillmentId = resolveFulfillmentId(input.get("fulfillment_id"), sorReference);

        Map<String, Object> ackEvent = new HashMap<>();
        ackEvent.put("fulfillment_id", fulfillmentId);
        ackEvent.put("milestone", milestone);
        ackEvent.put("outcome", outcome);
        ackEvent.put("reason", reason);

        // Synchronous by way of Events: if this publish fails the function throws, the flow fails,
        // and the inbound SoR record is retried and ultimately dead-lettered. A fire-and-forget
        // send would commit the inbound offset and lose the milestone silently, leaving the
        // steward suspended until its 8-hour TTL expired.
        Events.publish(headers, instance, "fulfillment.ack", fulfillmentId, ackEvent);

        log.info("Normalized {} ack for fulfillment {} (outcome={}, sor_reference={})",
                milestone, fulfillmentId, outcome, sorReference);

        Map<String, Object> result = new HashMap<>();
        result.put("forwarded", true);
        result.put("fulfillment_id", fulfillmentId);
        result.put("milestone", milestone);
        return result;
    }

    /**
     * The SoR identifies work by sor_reference; the steward by fulfillment_id. Translating between
     * the two identity schemes is part of this layer's job. An explicit fulfillment_id is trusted
     * when the SoR echoes one back, otherwise it is recovered from the reference the steward
     * minted at dispatch.
     */
    private String resolveFulfillmentId(Object explicit, String sorReference) {
        if (explicit instanceof String s && !s.isBlank()) {
            return s;
        }
        if (sorReference != null && sorReference.startsWith(SOR_REFERENCE_PREFIX)) {
            return sorReference.substring(SOR_REFERENCE_PREFIX.length());
        }
        throw new IllegalArgumentException(
                "Cannot resolve fulfillment_id from sor_reference: " + sorReference);
    }
}
