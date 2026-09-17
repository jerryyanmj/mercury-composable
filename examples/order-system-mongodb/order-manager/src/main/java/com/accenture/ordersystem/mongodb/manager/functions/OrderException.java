package com.accenture.ordersystem.mongodb.manager.functions;

import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.TypedLambdaFunction;

import java.util.HashMap;
import java.util.Map;

@PreLoad(route = "v1.order.exception", instances = 5)
public class OrderException implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers,
                                           Map<String, Object> input, int instance) {
        int status = input.get("status") instanceof Number n ? n.intValue() : 500;
        String message = (String) input.get("message");

        Map<String, Object> result = new HashMap<>();
        result.put("status", status);
        result.put("message", message != null ? message : "Internal error");
        result.put("error", true);
        return result;
    }
}
