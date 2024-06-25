package com.accenture.tasks;

import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.TypedLambdaFunction;
import org.platformlambda.core.util.Utility;

import java.util.Map;

@PreLoad(route="decision.case.one, decision.case.two, echo.one, echo.two, echo.three, echo.four, echo.ext1, echo.ext2", instances=10)
public class Echo implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {

    private static final String EXCEPTION = "exception";
    private static final String BREAK = "break";
    private static final String CONTINUE = "continue";
    private static final String JUMP = "jump";

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers, Map<String, Object> input, int instance) {
        if (input.containsKey(EXCEPTION)) {
            throw new IllegalArgumentException((String) input.get(EXCEPTION));
        }
        if (input.containsKey(BREAK)) {
            int breakAt = Utility.getInstance().str2int(input.get(BREAK).toString());
            Object o = input.get("n");
            if (o instanceof Integer) {
                int n = (Integer) o;
                if (n == breakAt) {
                    input.put("quit", true);
                }
            }
        }
        if (input.containsKey(CONTINUE)) {
            int skip = Utility.getInstance().str2int(input.get(CONTINUE).toString());
            Object o = input.get("n");
            if (o instanceof Integer) {
                int n = (Integer) o;
                if (n == skip) {
                    input.put("continue", true);
                }
            }
        }
        if (input.containsKey(JUMP)) {
            int skip = Utility.getInstance().str2int(input.get(JUMP).toString());
            Object o = input.get("n");
            if (o instanceof Integer) {
                int n = (Integer) o;
                if (n == skip) {
                    input.put("jump", true);
                }
            }
        }
        return input;
    }
}
