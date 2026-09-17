package com.accenture.planfunds.functions;

import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.TypedLambdaFunction;

import java.util.Map;

/**
 * Mock Plan System — returns plan configuration including the default investment fund.
 *
 * Real implementation: call the actual plan service here.
 */
@PreLoad(route = "mock.plan.service", instances = 10)
public class MockPlanService implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {

    private static final Map<String, Map<String, Object>> PLANS = Map.of(
        "PLAN-001", Map.of(
            "plan_id", "PLAN-001",
            "plan_name", "Acme 401(k)",
            "default_investment_fund_id", "FUND-A"
        ),
        "PLAN-002", Map.of(
            "plan_id", "PLAN-002",
            "plan_name", "Acme Pension",
            "default_investment_fund_id", "FUND-C"
        ),
        "PLAN-003", Map.of(
            "plan_id", "PLAN-003",
            "plan_name", "Acme ESOP",
            "default_investment_fund_id", "FUND-B"
        )
    );

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers,
                                           Map<String, Object> input, int instance) throws Exception {
        String planId = (String) input.get("plan_id");
        Map<String, Object> plan = PLANS.get(planId);
        if (plan == null) {
            throw new IllegalArgumentException("Plan not found: " + planId);
        }
        return plan;
    }
}
