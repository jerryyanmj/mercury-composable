package com.accenture.ordersystem.sormock.functions;

import com.accenture.ordersystem.common.Demo;
import com.accenture.ordersystem.common.Events;
import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.TypedLambdaFunction;
import org.platformlambda.core.system.PostOffice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * v1.sor.mock.process - stands in for the System of Record.
 *
 * It deliberately behaves the way the real SoR does rather than the way we would prefer: each
 * milestone is published on its OWN topic, the payload carries no milestone field, and work is
 * identified by sor_reference. Order Acknowledge absorbs that shape.
 *
 * A payload may carry "fail_milestone" to make that milestone come back with outcome=fail; the
 * mock then stops, as a real SoR would abandon the work.
 *
 * Input : {fulfillment_id, sor_reference, payload}
 * Output: {acked: N}
 */
@PreLoad(route = "v1.sor.mock.process", instances = 10)
public class SorMockProcess implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(SorMockProcess.class);

    /** Milestone name (as the steward knows it) paired with the SoR topic that implies it. */
    private static final List<String[]> MILESTONES = List.of(
            new String[] {"validated",    "sor.validation"},
            new String[] {"preprocessed", "sor.preprocess"},
            new String[] {"processed",    "sor.process"});

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers,
                                           Map<String, Object> input,
                                           int instance) throws Exception {
        String fulfillmentId = (String) input.get("fulfillment_id");
        String sorReference = (String) input.get("sor_reference");
        if (fulfillmentId == null) {
            throw new IllegalArgumentException("Missing fulfillment_id");
        }
        String failMilestone = null;
        if (input.get("payload") instanceof Map<?, ?> payload) {
            Object directive = payload.get("fail_milestone");
            failMilestone = directive == null ? null : String.valueOf(directive);
        }
        // In demo mode the SoR stays silent until a milestone is requested through
        // POST /api/v1/sor/{milestone}, so each step of the lifecycle can be shown deliberately.
        if (Demo.enabled()) {
            log.info("Demo mode: holding {} - acknowledge it via POST /api/v1/sor/<milestone>",
                    fulfillmentId);
            Map<String, Object> held = new HashMap<>();
            held.put("acked", 0);
            held.put("held", true);
            held.put("fulfillment_id", fulfillmentId);
            held.put("sor_reference", sorReference);
            return held;
        }
        PostOffice po = new PostOffice(headers, instance);

        int acked = 0;
        for (String[] milestone : MILESTONES) {
            String name = milestone[0];
            String topic = milestone[1];
            boolean failed = name.equals(failMilestone);

            // Note what is NOT here: no milestone field. The topic carries that meaning.
            Map<String, Object> ack = new HashMap<>();
            ack.put("sor_reference", sorReference);
            ack.put("fulfillment_id", fulfillmentId);
            ack.put("outcome", failed ? "fail" : "pass");
            ack.put("reason", failed ? "SoR rejected the work at the " + name + " stage" : null);

            Events.publish(po, topic, fulfillmentId, ack);
            acked++;
            log.info("SoR mock: published {} to {} for fulfillment {}",
                    failed ? "fail" : "pass", topic, fulfillmentId);

            if (failed) {
                break;
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("acked", acked);
        return result;
    }
}
