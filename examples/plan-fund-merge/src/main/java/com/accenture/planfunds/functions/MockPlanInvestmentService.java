package com.accenture.planfunds.functions;

import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.TypedLambdaFunction;

import java.util.List;
import java.util.Map;

/**
 * Mock Plan Investment System — returns the list of funds and their market value balances for a plan.
 *
 * Real implementation: call the actual investment system here.
 */
@PreLoad(route = "mock.plan.investment.service", instances = 10)
public class MockPlanInvestmentService implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {

    private static final Map<String, List<Map<String, Object>>> FUND_BALANCES = Map.of(
        "PLAN-001", List.of(
            Map.of("fund_id", "FUND-A", "fund_name", "US Large Cap Growth",   "market_value", 125000.00),
            Map.of("fund_id", "FUND-B", "fund_name", "International Equity",  "market_value",  48500.00),
            Map.of("fund_id", "FUND-D", "fund_name", "Bond Index",            "market_value",  31200.00)
        ),
        "PLAN-002", List.of(
            Map.of("fund_id", "FUND-B", "fund_name", "International Equity",  "market_value",  72000.00),
            Map.of("fund_id", "FUND-C", "fund_name", "Stable Value",          "market_value", 210000.00),
            Map.of("fund_id", "FUND-D", "fund_name", "Bond Index",            "market_value",  55000.00)
        ),
        "PLAN-003", List.of(
            Map.of("fund_id", "FUND-A", "fund_name", "US Large Cap Growth",   "market_value",  98000.00),
            Map.of("fund_id", "FUND-B", "fund_name", "International Equity",  "market_value",  43000.00),
            Map.of("fund_id", "FUND-E", "fund_name", "Small Cap Value",       "market_value",  17500.00)
        )
    );

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers,
                                           Map<String, Object> input, int instance) throws Exception {
        String planId = (String) input.get("plan_id");
        List<Map<String, Object>> funds = FUND_BALANCES.get(planId);
        if (funds == null) {
            throw new IllegalArgumentException("No investment data for plan: " + planId);
        }
        return Map.of("funds", funds);
    }
}
