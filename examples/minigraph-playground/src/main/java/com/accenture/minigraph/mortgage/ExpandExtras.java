/*
    Copyright 2018-2026 Accenture Technology
    Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.accenture.minigraph.mortgage;

import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.TypedLambdaFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reshape helper (NO financial calculation): expand the optional extra-payment inputs into a
 * per-month array {@code extras[1..N]} that the graph's for_each amortization loop iterates.
 * graph.math cannot build an array with dynamic-index writes, which is the only reason this exists.
 * <p>
 * Input: {@code term_years}, and optionally {@code extra_monthly}, {@code extra_annual},
 * {@code lump_sums:[{month,amount}, ...]} (each {@code month} &gt; 1).
 * Output: {@code {extras:[...N doubles...], months:N}}.
 */
@PreLoad(route = "v1.mortgage.expand-extras", instances = 50)
public class ExpandExtras implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers, Map<String, Object> input, int instance) {
        int n = (int) Math.round(num(input.get("term_years")) * 12);
        if (n <= 0) {
            throw new IllegalArgumentException("term_years must be positive");
        }
        double monthly = optional(input.get("extra_monthly"));
        double annual = optional(input.get("extra_annual"));
        double[] extras = new double[n];
        for (int m = 1; m <= n; m++) {
            extras[m - 1] = monthly + (m % 12 == 0 ? annual : 0.0);
        }
        Object lumpList = input.get("lump_sums");
        if (lumpList instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> e)) {
                    throw new IllegalArgumentException("each lump_sums entry must be a {month, amount} object");
                }
                int month = (int) num(((Map<String, Object>) e).get("month"));
                double amount = num(((Map<String, Object>) e).get("amount"));
                if (month <= 1 || month > n) {
                    throw new IllegalArgumentException("lump_sums month must be between 2 and " + n + ", got: " + month);
                }
                extras[month - 1] += amount;
            }
        }
        List<Double> out = new ArrayList<>(n);
        for (double v : extras) {
            out.add(Math.round(v * 100.0) / 100.0);
        }
        // also default the optional escrow inputs to 0 so the graph's graph.math nodes never
        // see a null (graph.math has no null-coalescing) - this is input preparation, not finance
        double tax = optional(input.get("annual_property_tax"));
        double insurance = optional(input.get("annual_home_insurance"));
        return Map.of("extras", out, "months", n, "tax", tax, "insurance", insurance);
    }

    private double num(Object v) {
        if (v instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("expected a number, got: " + v);
        }
    }

    private double optional(Object v) {
        return v == null ? 0.0 : num(v);
    }
}
