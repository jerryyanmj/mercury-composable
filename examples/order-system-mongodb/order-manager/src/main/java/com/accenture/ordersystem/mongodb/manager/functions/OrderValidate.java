package com.accenture.ordersystem.mongodb.manager.functions;

import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.TypedLambdaFunction;

import java.util.Map;

@PreLoad(route = "v1.order.validate", instances = 5)
public class OrderValidate implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers,
                                           Map<String, Object> input, int instance) {
        String orderId = (String) input.get("order_id");
        String fulfillmentId = (String) input.get("fulfillment_id");
        String customerId = (String) input.get("customer_id");
        Object amount = input.get("amount");

        if (orderId == null || !orderId.startsWith("O")) {
            throw new IllegalArgumentException("order_id must start with 'O'");
        }
        if (fulfillmentId == null || !fulfillmentId.startsWith("F")) {
            throw new IllegalArgumentException("fulfillment_id must start with 'F'");
        }
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customer_id is required");
        }
        if (amount == null) {
            throw new IllegalArgumentException("amount is required");
        }

        return input;
    }
}
