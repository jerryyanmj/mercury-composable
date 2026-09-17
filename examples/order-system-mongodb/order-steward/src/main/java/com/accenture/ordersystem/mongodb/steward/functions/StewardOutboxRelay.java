package com.accenture.ordersystem.mongodb.steward.functions;

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

@PreLoad(route = "v1.steward.outbox.relay", instances = 1)
public class StewardOutboxRelay implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(StewardOutboxRelay.class);

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers,
                                           Map<String, Object> input, int instance) throws Exception {
        if (Demo.suppressed(headers)) {
            return Map.of("status", "suppressed");
        }

        Db db = Db.using(headers, instance);
        PostOffice po = db.postOffice();

        Map<String, Object> filter = new HashMap<>();
        filter.put("outbox", Map.of("$elemMatch", nullableMap("sent_at", null)));

        List<Map<String, Object>> docs = db.findMany("fulfillments", filter, 100);
        int published = 0;

        for (Map<String, Object> doc : docs) {
            String fulfillmentId = (String) doc.get("fulfillment_id");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> outbox = (List<Map<String, Object>>) doc.get("outbox");
            if (outbox == null) continue;

            for (Map<String, Object> entry : outbox) {
                if (entry.get("sent_at") != null) continue;
                String entryId = (String) entry.get("id");
                String topic = (String) entry.get("topic");
                Object payload = entry.get("payload");
                if (entryId == null || topic == null) continue;

                EventEnvelope notification = new EventEnvelope()
                        .setTo("simple.kafka.notification")
                        .setHeader("topic", topic)
                        .setHeader("cid", fulfillmentId)
                        .setBody(payload);
                po.request(notification, 10_000).get();

                String now = Instant.now().toString();
                Map<String, Object> arrayFilter = new HashMap<>();
                arrayFilter.put("e.id", entryId);
                db.updateOne("fulfillments",
                        Map.of("fulfillment_id", fulfillmentId),
                        Map.of("$set", Map.of("outbox.$[e].sent_at", now)),
                        List.of(arrayFilter));

                published++;
                log.debug("Relayed {} outbox entry {} for fulfillment {}", topic, entryId, fulfillmentId);
            }
        }

        log.info("Steward outbox relay: published {} events", published);
        return Map.of("published", published);
    }

    private static Map<String, Object> nullableMap(String key, Object value) {
        Map<String, Object> m = new HashMap<>();
        m.put(key, value);
        return m;
    }
}
