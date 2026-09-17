package com.accenture.ordersystem.mongodb.manager.functions;

import com.accenture.ordersystem.mongodb.common.Db;
import com.accenture.ordersystem.mongodb.common.Demo;
import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.EventEnvelope;
import org.platformlambda.core.models.TypedLambdaFunction;
import org.platformlambda.core.system.PostOffice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@PreLoad(route = "v1.manager.outbox.relay", instances = 1)
public class ManagerOutboxRelay implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(ManagerOutboxRelay.class);

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers,
                                           Map<String, Object> input, int instance) throws Exception {
        if (Demo.suppressed(headers)) {
            return Map.of("status", "suppressed");
        }

        Db db = Db.using(headers, instance);
        PostOffice po = db.postOffice();

        // Find orders with unsent outbox entries
        Map<String, Object> filter = new HashMap<>();
        filter.put("outbox", Map.of("$elemMatch", nullableMap("sent_at", null)));

        List<Map<String, Object>> docs = db.findMany("orders", filter, 100);
        int published = 0;

        for (Map<String, Object> doc : docs) {
            String orderId = (String) doc.get("order_id");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> outbox = (List<Map<String, Object>>) doc.get("outbox");
            if (outbox == null) continue;

            for (Map<String, Object> entry : outbox) {
                if (entry.get("sent_at") != null) continue;
                String entryId = (String) entry.get("id");
                String topic = (String) entry.get("topic");
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = (Map<String, Object>) entry.get("payload");
                if (entryId == null || topic == null) continue;

                // The fulfillment CID propagates to Kafka as the "cid" header so the steward
                // can resume the graph session for the same fulfillment across events.
                String fulfillmentCid = payload != null ? (String) payload.get("fulfillment_id") : null;

                // Publish to Kafka via simple.kafka.notification
                EventEnvelope notification = new EventEnvelope()
                        .setTo("simple.kafka.notification")
                        .setHeader("topic", topic)
                        .setBody(payload);
                if (fulfillmentCid != null) {
                    notification.setHeader("cid", fulfillmentCid);
                }
                po.request(notification, 10_000).get();

                // Mark sent with arrayFilter
                String now = Instant.now().toString();
                Map<String, Object> arrayFilter = new HashMap<>();
                arrayFilter.put("e.id", entryId);
                db.updateOne("orders",
                        Map.of("order_id", orderId),
                        Map.of("$set", Map.of("outbox.$[e].sent_at", now)),
                        List.of(arrayFilter));
                published++;
                log.debug("Relayed {} outbox entry {} for order {}", topic, entryId, orderId);
            }
        }

        log.info("Manager outbox relay: published {} events", published);
        return Map.of("published", published);
    }

    private static Map<String, Object> nullableMap(String key, Object value) {
        Map<String, Object> m = new HashMap<>();
        m.put(key, value);
        return m;
    }
}
