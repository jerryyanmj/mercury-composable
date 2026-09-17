package com.accenture.ordersystem.manager.functions;

import com.accenture.ordersystem.common.Db;
import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.TypedLambdaFunction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * v1.order.status - the external report: order + its lines + each line's history.
 *
 * Reads only the manager's own read model. It never queries the steward, which is the point of
 * the CQRS projection - this whole view is rebuilt from fulfillment.status events.
 *
 * Input : {order_id}
 * Output: {order, lines[], history[]} or {found: false}
 */
@PreLoad(route = "v1.order.status", instances = 10)
public class OrderStatus implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {

    private static final String ORDER =
            "SELECT order_id, external_party, order_status, status_version, updated_at " +
            "FROM order_view WHERE order_id = :order_id";
    private static final String LINES =
            "SELECT fulfillment_id, line_status FROM line_view WHERE order_id = :order_id " +
            "ORDER BY fulfillment_id";
    private static final String HISTORY =
            "SELECT h.fulfillment_id, h.seq, h.from_state, h.to_state, h.reason, h.changed_at " +
            "FROM line_history h JOIN routing_map r USING (fulfillment_id) " +
            "WHERE r.order_id = :order_id ORDER BY h.fulfillment_id, h.seq";

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers,
                                           Map<String, Object> input,
                                           int instance) throws Exception {
        String orderId = (String) input.get("order_id");
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("Missing order_id");
        }
        Db db = Db.using(headers, instance);
        Map<String, Object> params = Map.of("order_id", orderId);

        Map<String, Object> result = new HashMap<>();
        Map<String, Object> order = db.queryOne(ORDER, params).orElse(null);
        if (order == null) {
            result.put("found", false);
            result.put("order_id", orderId);
            return result;
        }
        List<Map<String, Object>> lines = db.query(LINES, params);
        List<Map<String, Object>> history = db.query(HISTORY, params);

        result.put("found", true);
        result.put("order", order);
        result.put("lines", lines);
        result.put("history", history);
        result.put("transitions", history.size());
        return result;
    }
}
