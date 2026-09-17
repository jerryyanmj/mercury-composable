package com.accenture.ordersystem.common;

import org.platformlambda.core.util.AppConfigReader;

import java.util.Map;

/**
 * Demo mode: start an app with DEMO_MODE=true to drive the pipeline one step at a time.
 *
 * The scheduled jobs (both outbox relays and the dispatch window) keep ticking but do nothing,
 * and the SoR mock stops acknowledging on its own. Every step is then invoked deliberately
 * through a REST endpoint, so a demo can pause between them and show the state each one produced.
 *
 * A manual invocation is distinguished from the scheduler by a flag the REST flow maps in, which
 * is why the guard needs the function's input rather than just the configuration.
 */
public final class Demo {

    private static final String TRIGGER = "trigger";
    private static final String MANUAL = "manual";

    private Demo() {
    }

    /** True when the app was started with DEMO_MODE=true. */
    public static boolean enabled() {
        return "true".equalsIgnoreCase(
                AppConfigReader.getInstance().getProperty("demo.mode", "false"));
    }

    /** True when this invocation came from a demo endpoint rather than the scheduler. */
    public static boolean manuallyTriggered(Map<String, Object> input) {
        return input != null && MANUAL.equals(input.get(TRIGGER));
    }

    /**
     * True when a scheduled job should do nothing: demo mode is on and nobody asked for this run.
     */
    public static boolean suppressed(Map<String, Object> input) {
        return enabled() && !manuallyTriggered(input);
    }
}
