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
import java.util.UUID;

/**
 * v1.mock.order.submit — publishes an order event to orders.inbound as if sent by an external party.
 * Used for end-to-end testing via POST /api/mock/order.
 */
@PreLoad(route = "v1.mock.order.submit", instances = 5)
public class MockOrderSubmit implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(MockOrderSubmit.class);

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers,
                                           Map<String, Object> input, int instance) throws Exception {
        String orderId = (String) input.get("order_id");
        if (orderId == null) throw new IllegalArgumentException("Missing order_id");

        // Generate fulfillment_id server-side if not supplied — matches order-system (Postgres) behavior
        String fulfillmentId = (String) input.get("fulfillment_id");
        if (fulfillmentId == null || fulfillmentId.isBlank()) {
            String hex = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            fulfillmentId = "FUL-" + orderId + "-" + hex;
        }

        Map<String, Object> message = new HashMap<>(input);
        message.put("fulfillment_id", fulfillmentId);

        PostOffice po = new PostOffice(headers, instance);
        EventEnvelope notification = new EventEnvelope()
                .setTo("simple.kafka.notification")
                .setHeader("topic", Events.ORDERS_INBOUND)
                .setBody(message);
        po.request(notification, 10_000).get();

        log.info("Submitted test order {} / fulfillment {}", orderId, fulfillmentId);
        // Return {topic, order_id} to match order-system (Postgres) mock interface
        return Map.of("topic", Events.ORDERS_INBOUND, "order_id", orderId);
    }
}
