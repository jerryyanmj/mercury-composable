package com.accenture.ordersystem.manager.functions;

import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.TypedLambdaFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * v1.order.exception - the manager's flow exception handler.
 *
 * Wired as `exception:` on both manager flows, so a failure anywhere in either flow arrives here
 * as configuration rather than as an unhandled stack trace. For the REST intake that means the
 * caller gets a structured body and a sensible status; for the Kafka projection it means the
 * failure is surfaced (and the record retried, then dead-lettered) instead of vanishing.
 *
 * Input : {status, message} mapped from the flow's error context
 * Output: {status, message, error: true}
 */
@PreLoad(route = "v1.order.exception", instances = 5)
public class OrderException implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(OrderException.class);

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers,
                                           Map<String, Object> input,
                                           int instance) {
        int status = input.get("status") instanceof Number n ? n.intValue() : 500;
        String message = input.get("message") == null ? "Unexpected failure" : String.valueOf(input.get("message"));

        log.warn("Order flow failed: {} - {}", status, message);

        Map<String, Object> result = new HashMap<>();
        result.put("status", status);
        result.put("message", message);
        result.put("error", true);
        return result;
    }
}
