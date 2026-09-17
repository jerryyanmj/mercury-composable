package com.accenture.ordersystem.mongodb.common;

import org.platformlambda.core.util.AppConfigReader;

import java.util.Map;

public final class Demo {
    private static final String DEMO_MODE = "demo.mode";

    private Demo() {}

    public static boolean suppressed(Map<String, String> headers) {
        String trigger = headers.get("trigger");
        if ("manual".equals(trigger)) return false;
        return isEnabled();
    }

    public static boolean isEnabled() {
        AppConfigReader cfg = AppConfigReader.getInstance();
        return "true".equalsIgnoreCase(cfg.getProperty(DEMO_MODE, "false"));
    }
}
