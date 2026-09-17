package com.accenture.ordersystem.manager.functions;

import com.accenture.ordersystem.common.Db;
import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.TypedLambdaFunction;
import org.platformlambda.core.serializers.SimpleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * v1.order.persist - accept an order and enqueue its unit of work.
 *
 * The order, its routing line and the outbox event all commit together, so an accepted order can
 * never exist without the event that hands it to a steward. The caller's request returns as soon
 * as that one transaction commits - it never waits on Kafka.
 *
 * Input : {order_id, external_party, payload, route, fulfillment_id (minted by the flow)}
 * Output: {order_id, fulfillment_id, status}
 */
@PreLoad(route = "v1.order.persist", instances = 10)
public class OrderPersist implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(OrderPersist.class);

    private static final String CREATE_ORDER =
            "INSERT INTO \"order\"(order_id, external_party, payload, order_status, status_version, " +
            "created_at, updated_at) VALUES (:order_id, :external_party, :payload::jsonb, " +
            "'RECEIVED', 1, now(), now()) ON CONFLICT (order_id) DO NOTHING";
    private static final String CREATE_ROUTING_LINE =
            "INSERT INTO routing_map(order_id, fulfillment_id, line_status, route) " +
            "VALUES (:order_id, :fulfillment_id, 'RECEIVED', :route) " +
            "ON CONFLICT (fulfillment_id) DO NOTHING";
    // The topic is the destination: the relay publishes wherever this row says.
    private static final String STAGE_REQUEST =
            "INSERT INTO outbox(aggregate_id, topic, payload, headers, created_at) " +
            "VALUES (:aggregate_id, 'fulfillment.request', :payload::jsonb, '{}'::jsonb, now())";

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers,
                                           Map<String, Object> input,
                                           int instance) throws Exception {
        String orderId = (String) input.get("order_id");
        String externalParty = (String) input.get("external_party");
        Object payload = input.get("payload");
        String route = (String) input.get("route");
        // Composed by the flow (order-intake -> mint-identity), so the id format stays in configuration.
        String fulfillmentId = (String) input.get("fulfillment_id");

        if (orderId == null || orderId.isBlank()) throw new IllegalArgumentException("Missing order_id");
        if (route == null || route.isBlank())     throw new IllegalArgumentException("Missing route");
        if (fulfillmentId == null || fulfillmentId.isBlank()) {
            throw new IllegalArgumentException("Missing fulfillment_id - the flow must mint it");
        }
        // The flow composes this id; assert the convention here so a change to the mapping cannot
        // quietly emit an identifier the rest of the system does not recognise.
        if (!fulfillmentId.startsWith("F")) {
            throw new IllegalArgumentException(
                    "fulfillment_id must start with 'F' - got: " + fulfillmentId);
        }

        Map<String, Object> fulfillmentRequest = new HashMap<>();
        fulfillmentRequest.put("fulfillment_id", fulfillmentId);
        fulfillmentRequest.put("order_id", orderId);
        fulfillmentRequest.put("route", route);
        fulfillmentRequest.put("payload", payload);

        Db.using(headers, instance).transaction()
                .add(CREATE_ORDER, Map.of("order_id", orderId,
                        "external_party", externalParty == null ? "UNKNOWN" : externalParty,
                        "payload", toJson(payload)))
                .add(CREATE_ROUTING_LINE, Map.of("order_id", orderId,
                        "fulfillment_id", fulfillmentId, "route", route))
                .add(STAGE_REQUEST, Map.of("aggregate_id", fulfillmentId,
                        "payload", toJson(fulfillmentRequest)))
                .execute();

        log.info("Order {} persisted, fulfillment {} enqueued", orderId, fulfillmentId);

        Map<String, Object> result = new HashMap<>();
        result.put("order_id", orderId);
        result.put("fulfillment_id", fulfillmentId);
        result.put("status", "RECEIVED");
        return result;
    }

    private static String toJson(Object value) {
        if (value == null) return "{}";
        if (value instanceof String s) return s;
        return SimpleMapper.getInstance().getMapper().writeValueAsString(value);
    }
}
