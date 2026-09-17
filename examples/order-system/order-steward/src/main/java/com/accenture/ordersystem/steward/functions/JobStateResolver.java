package com.accenture.ordersystem.steward.functions;

import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.TypedLambdaFunction;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scheduler state resolver: guards against a job iteration starting while the
 * previous one is still running. In-memory is sufficient here because the relay's
 * own SELECT ... FOR UPDATE SKIP LOCKED is what makes multi-instance safe.
 */
@PreLoad(route = "v1.job.state.resolver")
public class JobStateResolver implements TypedLambdaFunction<Map<String, Object>, Boolean> {

    private static final long MAX_RUN_MS = 60_000L;
    private static final Map<String, Long> RUNNING = new ConcurrentHashMap<>();

    @Override
    public Boolean handleEvent(Map<String, String> headers, Map<String, Object> input, int instance) {
        String type = headers.get("type");
        String name = headers.getOrDefault("name", "none");
        if ("none".equals(name)) {
            throw new IllegalArgumentException("missing job name");
        }
        return switch (type) {
            case "start" -> { RUNNING.put(name, System.currentTimeMillis()); yield true; }
            case "end" -> { RUNNING.remove(name); yield true; }
            case "expires" -> {
                Long started = RUNNING.get(name);
                yield started == null || System.currentTimeMillis() - started > MAX_RUN_MS;
            }
            default -> throw new IllegalArgumentException("type must be start, end or expires");
        };
    }
}
