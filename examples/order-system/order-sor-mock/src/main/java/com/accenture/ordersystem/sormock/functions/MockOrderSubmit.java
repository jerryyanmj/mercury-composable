package com.accenture.ordersystem.sormock.functions;

import com.accenture.ordersystem.common.Events;
import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.TypedLambdaFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * v1.mock.order.submit - stands in for the external party that places orders.
 *
 * Real orders arrive on the orders.inbound topic; this publishes one there so a demo (or a test)
 * can exercise the actual ingress rather than the REST convenience endpoint. It performs NO
 * validation of its own - a malformed order is exactly what you want to send when demonstrating
 * that the manager dead-letters it to orders.inbound.dlq.
 *
 * The order_id rides as the Kafka correlation id, which is how the manager's trace and the
 * eventual fulfillment stay stitched to the submission.
 *
 * Input : the order body {order_id, external_party, route, payload} - passed through verbatim
 * Output: {published, topic, order_id}
 */
@PreLoad(route = "v1.mock.order.submit", instances = 10)
public class MockOrderSubmit implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(MockOrderSubmit.class);

    private static final String TOPIC = "orders.inbound";

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers,
                                           Map<String, Object> input,
                                           int instance) throws Exception {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("Missing order body");
        }
        // Correlate on order_id when present. A deliberately malformed order may not have one, and
        // that must still reach the topic - proving the failure lands in the DLQ.
        Object orderId = input.get("order_id");
        String cid = orderId == null ? "unknown" : String.valueOf(orderId);

        Events.publish(headers, instance, TOPIC, cid, new HashMap<>(input));
        log.info("Mock external party: published order {} to {}", cid, TOPIC);

        Map<String, Object> result = new HashMap<>();
        result.put("published", true);
        result.put("topic", TOPIC);
        result.put("order_id", cid);
        return result;
    }
}
