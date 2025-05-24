package com.accenture.demo.tasks;

import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.TypedLambdaFunction;
import org.platformlambda.core.util.Utility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.accenture.demo.tasks.ProcessData.ROUTE;


@PreLoad(route = ROUTE)
public class ProcessData implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {

    public static final String ROUTE = "v1.process.data";
    private static final Logger log = LoggerFactory.getLogger(ProcessData.class);

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers, Map<String, Object> input, int instance) throws Exception {
        int attempt = Utility.getInstance().str2int(input.getOrDefault("attempt", 0).toString());
        log.info("Route: {}, Instance: {} started attemp {}", ROUTE, instance, attempt);
        if (input.containsKey("mode") && "error".equals(input.get("mode")) ) {
            throw new IllegalArgumentException("Error occurred");
        }
        return Map.of();
    }
}
