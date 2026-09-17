package com.accenture.ordersystem.mongodb.external.functions;

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
 * v1.sor.mock.process — receives a sor.dispatch event and auto-emits three SoR milestones
 * (validated, preprocessed, processed) unless demo mode suppresses auto-emission.
 *
 * In DEMO mode use POST /api/admin/sor/emit/{sor_reference}/{milestone}/{outcome} to
 * trigger each milestone manually.
 */
@PreLoad(route = "v1.sor.mock.process", instances = 10)
public class SorProcess implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(SorProcess.class);

    private static final List<String[]> MILESTONES = List.of(
            new String[]{"validated",   Events.SOR_VALIDATION},
            new String[]{"preprocessed", Events.SOR_PREPROCESS},
            new String[]{"processed",   Events.SOR_PROCESS}
    );

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers,
                                           Map<String, Object> input, int instance) throws Exception {
        String sorReference = (String) input.get("sor_reference");
        String fulfillmentId = (String) input.get("fulfillment_id");

        if (sorReference == null) throw new IllegalArgumentException("Missing sor_reference");

        log.info("SoR received dispatch for {} ({})", sorReference, fulfillmentId);

        if (Demo.suppressed(headers)) {
            log.info("DEMO MODE: holding milestones for {} — use admin endpoint to emit", sorReference);
            return Map.of("sor_reference", sorReference, "status", "held");
        }

        PostOffice po = new PostOffice(headers, instance);
        for (String[] pair : MILESTONES) {
            Map<String, Object> ackPayload = new HashMap<>();
            ackPayload.put("sor_reference", sorReference);
            ackPayload.put("outcome", "pass");
            ackPayload.put("reason", "");

            EventEnvelope notification = new EventEnvelope()
                    .setTo("simple.kafka.notification")
                    .setHeader("topic", pair[1])
                    .setHeader("cid", fulfillmentId)
                    .setBody(ackPayload);
            po.request(notification, 10_000).get();
            log.info("Emitted {} for {}", pair[0], sorReference);
        }

        return Map.of("sor_reference", sorReference, "status", "processed", "milestones", 3);
    }
}
