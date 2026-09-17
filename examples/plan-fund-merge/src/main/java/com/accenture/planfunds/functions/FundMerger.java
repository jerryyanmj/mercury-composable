package com.accenture.planfunds.functions;

import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.TypedLambdaFunction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enriches each fund with default=true when its fund_id matches the plan's default_investment_fund_id.
 */
@PreLoad(route = "fund.merger", instances = 10)
public class FundMerger implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers,
                                           Map<String, Object> input, int instance) throws Exception {
        String defaultFundId = (String) input.get("default_fund_id");
        List<?> rawFunds = input.get("funds") instanceof List<?> l ? l : List.of();

        List<Map<String, Object>> enriched = new ArrayList<>();
        boolean defaultFound = false;
        for (Object item : rawFunds) {
            if (item instanceof Map<?, ?> raw) {
                Map<String, Object> fund = new HashMap<>();
                raw.forEach((k, v) -> fund.put(String.valueOf(k), v));
                boolean isDefault = !defaultFound
                        && fund.get("fund_id") != null
                        && fund.get("fund_id").equals(defaultFundId);
                fund.put("default", isDefault);
                if (isDefault) defaultFound = true;
                enriched.add(fund);
            }
        }
        return Map.of("funds", enriched);
    }
}
