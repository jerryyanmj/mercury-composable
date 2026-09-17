package com.accenture.ordersystem.mongodb.manager.functions;

import com.accenture.ordersystem.mongodb.common.Db;
import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.TypedLambdaFunction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@PreLoad(route = "v1.order.status", instances = 10)
public class OrderStatus implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers,
                                           Map<String, Object> input, int instance) throws Exception {
        String orderId = (String) input.get("order_id");
        if (orderId == null) throw new IllegalArgumentException("Missing order_id");

        Db db = Db.using(headers, instance);
        Map<String, Object> doc = db.findOneOrFail("orders",
                Map.of("order_id", orderId),
                "Order not found: " + orderId);

        // Shape the response to match order-system (Postgres): {found, order, lines, transitions, history}
        List<?> lines   = doc.get("lines")   instanceof List<?> l ? l : List.of();
        List<?> history = doc.get("history") instanceof List<?> h ? h : List.of();

        Map<String, Object> order = new HashMap<>(doc);
        order.remove("outbox");
        order.remove("lines");
        order.remove("history");

        Map<String, Object> response = new HashMap<>();
        response.put("found", true);
        response.put("order", order);
        response.put("lines", lines);
        response.put("transitions", history.size());
        response.put("history", history);
        return response;
    }
}
