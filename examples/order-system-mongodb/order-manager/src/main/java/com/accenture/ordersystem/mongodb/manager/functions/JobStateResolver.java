package com.accenture.ordersystem.mongodb.manager.functions;

import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.TypedLambdaFunction;

import java.util.Map;

@PreLoad(route = "v1.job.state.resolver", instances = 1)
public class JobStateResolver implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers,
                                           Map<String, Object> input, int instance) {
        return Map.of("execute", true);
    }
}
