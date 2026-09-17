/*
    Copyright 2018-2026 Accenture Technology
    Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.accenture.mortgage;

import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.TypedLambdaFunction;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reshape/date helper (NO financial calculation): the graph computes the amortization into
 * parallel numeric arrays; this zips them into dated row objects and computes the payoff date.
 * graph.math has no calendar arithmetic and cannot build an array-of-objects, which is the only
 * reason this exists.
 * <p>
 * Input: the parallel arrays {@code months, payments, principals, interests, extras, balances},
 * plus {@code start_date} (ISO yyyy-MM-dd) and {@code payoff_month} (rows past it are the
 * paid-off tail and are trimmed).
 * Output: {@code {schedule:[{month,date,payment,principal,interest,extra,balance}, ...], payoff_date}}.
 */
@PreLoad(route = "v1.mortgage.format-schedule", instances = 50)
public class FormatSchedule implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers, Map<String, Object> input, int instance) {
        LocalDate start = parseDate(input.get("start_date"));
        int payoff = (int) num(input.get("payoff_month"));
        List<?> months = list(input.get("months"));
        List<?> payments = list(input.get("payments"));
        List<?> principals = list(input.get("principals"));
        List<?> interests = list(input.get("interests"));
        List<?> extras = list(input.get("extras"));
        List<?> balances = list(input.get("balances"));

        int rows = payoff > 0 ? Math.min(payoff, months.size()) : months.size();
        List<Map<String, Object>> schedule = new ArrayList<>(rows);
        for (int k = 0; k < rows; k++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", (int) num(months.get(k)));
            row.put("date", start.plusMonths(k).toString());
            row.put("payment", num(payments.get(k)));
            row.put("principal", num(principals.get(k)));
            row.put("interest", num(interests.get(k)));
            row.put("extra", num(extras.get(k)));
            row.put("balance", num(balances.get(k)));
            schedule.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schedule", schedule);
        result.put("payoff_date", start.plusMonths((payoff > 0 ? payoff : rows) - 1L).toString());
        return result;
    }

    private List<?> list(Object v) {
        if (v instanceof List<?> l) {
            return l;
        }
        throw new IllegalArgumentException("expected a list, got: " + v);
    }

    private double num(Object v) {
        if (v instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(v).trim());
    }

    private LocalDate parseDate(Object v) {
        try {
            return LocalDate.parse(String.valueOf(v).trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("start_date must be ISO yyyy-MM-dd, got: " + v);
        }
    }
}
