package com.accenture.ordersystem.manager.functions;

import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.TypedLambdaFunction;

import java.util.HashMap;
import java.util.Map;

/**
 * v1.order.validate - the intake guard, and deliberately the FIRST task of order-intake.
 *
 * It runs before the identity is minted because the minting step composes the fulfillment id with
 * f:concat over order_id: a null there fails inside the mapping engine, which aborts the flow
 * before any task executes and therefore never reaches the flow's exception handler - the caller
 * got a bare 500. Validating first keeps every bad request on the handled path.
 *
 * A presence check is a guard rather than orchestration, so it stays in code; what the flow
 * expresses is WHEN it runs.
 *
 * Input : {order_id, route, payload, external_party}
 * Output: the same body, unchanged, when valid
 */
@PreLoad(route = "v1.order.validate", instances = 10)
public class OrderValidate implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {

    /** Every order identifier in this system starts with 'O'. */
    private static final String ORDER_ID_PREFIX = "O";

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers,
                                           Map<String, Object> input,
                                           int instance) {
        String orderId = require(input, "order_id");
        require(input, "route");
        if (input.get("payload") == null) {
            throw new IllegalArgumentException("Missing payload");
        }
        // Rejected, never corrected: an order is not modified to fit the convention, because the
        // caller's identifier is the key every downstream component correlates on.
        if (!orderId.startsWith(ORDER_ID_PREFIX)) {
            throw new IllegalArgumentException(
                    "order_id must start with '" + ORDER_ID_PREFIX + "' - got: " + orderId);
        }
        return new HashMap<>(input);
    }

    private static String require(Map<String, Object> input, String field) {
        Object value = input.get(field);
        if (!(value instanceof String s) || s.isBlank()) {
            throw new IllegalArgumentException("Missing " + field);
        }
        return s;
    }
}
