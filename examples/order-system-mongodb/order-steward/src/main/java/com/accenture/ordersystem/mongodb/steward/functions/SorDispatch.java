package com.accenture.ordersystem.mongodb.steward.functions;

import com.accenture.ordersystem.mongodb.common.Db;
import com.accenture.ordersystem.mongodb.common.Events;
import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.TypedLambdaFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * v1.sor.dispatch — appends a sor.dispatch outbox entry to the fulfillment document.
 *
 * The sor_reference is deterministic ("SOR-" + fulfillmentId) so re-delivery is benign:
 * the relay will publish a duplicate to sor.dispatch, but the SoR mock is idempotent on
 * sor_reference. The outbox entry id UUID guards against duplicate relay attempts for the
 * same dispatch event.
 */
@PreLoad(route = "v1.sor.dispatch", instances = 10)
public class SorDispatch implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(SorDispatch.class);

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers,
                                           Map<String, Object> input, int instance) throws Exception {
        String fulfillmentId = (String) input.get("fulfillment_id");
        if (fulfillmentId == null) throw new IllegalArgumentException("Missing fulfillment_id");

        Db db = Db.using(headers, instance);

        Map<String, Object> doc = db.findOneOrFail("fulfillments",
                Map.of("fulfillment_id", fulfillmentId),
                "Fulfillment not found: " + fulfillmentId);

        String sorReference = "SOR-" + fulfillmentId;
        String outboxId = UUID.randomUUID().toString();
        String now = Instant.now().toString();

        Map<String, Object> sorPayload = new HashMap<>();
        sorPayload.put("fulfillment_id", fulfillmentId);
        sorPayload.put("sor_reference", sorReference);
        sorPayload.put("payload", doc.get("payload"));

        Map<String, Object> outboxEntry = new HashMap<>();
        outboxEntry.put("id", outboxId);
        outboxEntry.put("topic", Events.SOR_DISPATCH);
        outboxEntry.put("payload", sorPayload);
        outboxEntry.put("sent_at", null);

        db.updateOne("fulfillments",
                Map.of("fulfillment_id", fulfillmentId),
                Map.of("$push", Map.of("outbox", outboxEntry),
                        "$set", Map.of("updated_at", now)),
                null);

        log.info("Staged sor.dispatch for fulfillment {} (sor_ref={})", fulfillmentId, sorReference);
        return Map.of("fulfillment_id", fulfillmentId, "sor_reference", sorReference);
    }
}
