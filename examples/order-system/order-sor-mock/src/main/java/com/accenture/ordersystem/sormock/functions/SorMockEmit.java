package com.accenture.ordersystem.sormock.functions;

import com.accenture.ordersystem.common.Events;
import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.TypedLambdaFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * v1.sor.mock.emit - publish ONE SoR milestone on demand.
 *
 * This is the demo counterpart to the automatic acknowledgement loop: it lets a presenter advance
 * the lifecycle one checkpoint at a time, and choose pass or fail for each, so the suspend/resume
 * behaviour is visible rather than instantaneous.
 *
 * The milestone determines the topic, exactly as the real SoR would publish it - the payload
 * carries no milestone field, because that meaning lives in the topic.
 *
 * Input : {milestone, fulfillment_id, outcome (default pass), reason}
 * Output: {published, topic, fulfillment_id, milestone, outcome}
 */
@PreLoad(route = "v1.sor.mock.emit", instances = 10)
public class SorMockEmit implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(SorMockEmit.class);

    private static final Map<String, String> TOPIC_OF = Map.of(
            "validated",    "sor.validation",
            "preprocessed", "sor.preprocess",
            "processed",    "sor.process");

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers,
                                           Map<String, Object> input,
                                           int instance) throws Exception {
        String milestone = (String) input.get("milestone");
        String fulfillmentId = (String) input.get("fulfillment_id");
        Object outcomeValue = input.get("outcome");
        String outcome = outcomeValue == null ? "pass" : String.valueOf(outcomeValue);
        Object reasonValue = input.get("reason");

        String topic = TOPIC_OF.get(milestone);
        if (topic == null) {
            throw new IllegalArgumentException(
                    "milestone must be one of validated, preprocessed, processed - got: " + milestone);
        }
        if (fulfillmentId == null || fulfillmentId.isBlank()) {
            throw new IllegalArgumentException("Missing fulfillment_id");
        }
        String reason = reasonValue != null ? String.valueOf(reasonValue)
                : ("fail".equals(outcome) ? "SoR rejected the work at the " + milestone + " stage" : null);

        Map<String, Object> ack = new HashMap<>();
        ack.put("sor_reference", "SOR-" + fulfillmentId);
        ack.put("fulfillment_id", fulfillmentId);
        ack.put("outcome", outcome);
        ack.put("reason", reason);

        Events.publish(headers, instance, topic, fulfillmentId, ack);
        log.info("Demo: published {} ({}) to {} for {}", milestone, outcome, topic, fulfillmentId);

        Map<String, Object> result = new HashMap<>();
        result.put("published", true);
        result.put("topic", topic);
        result.put("fulfillment_id", fulfillmentId);
        result.put("milestone", milestone);
        result.put("outcome", outcome);
        return result;
    }
}
