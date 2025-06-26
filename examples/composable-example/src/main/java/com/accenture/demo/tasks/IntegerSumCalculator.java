package com.accenture.demo.tasks;

import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.TypedLambdaFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;


@PreLoad(route = "v1.sum.list", instances = 1)
public class IntegerSumCalculator implements TypedLambdaFunction<Map<String, Object>, Integer> {
    private static final Logger log = LoggerFactory.getLogger(IntegerSumCalculator.class);

    @Override
    public Integer handleEvent(Map<String, String> headers, Map<String, Object> input, int instance) {
        var list = input.get("integer-list");
        if (list instanceof List<?>) {
            var intList = (List<Integer>) list;
            var sum = intList.stream().mapToInt(Integer::intValue).sum();
            log.info("Sum of {} is {}", intList, sum);
            return sum;
        }
        return 0;
    }
}
