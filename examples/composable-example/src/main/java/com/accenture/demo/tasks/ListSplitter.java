package com.accenture.demo.tasks;

import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.TypedLambdaFunction;
import org.platformlambda.core.util.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@PreLoad(route = "v1.list.splitter", instances = 1)
public class ListSplitter implements TypedLambdaFunction<Map<String, Object>, List<List<Integer>>> {
    @Override
    public List<List<Integer>> handleEvent(Map<String, String> headers, Map<String, Object> input, int instance) {
        int total = Utility.getInstance().str2int(input.get("total").toString());
        int size = Utility.getInstance().str2int(input.get("size").toString());
        List<List<Integer>> result = new ArrayList<>();
        int baseSize = total / size;
        int remainder = total % size;

        int current = 1;

        for (int i = 0; i < size; i++) {
            int chunkSize = baseSize + (i < remainder ? 1 : 0);
            List<Integer> sublist = new ArrayList<>();

            for (int j = 0; j < chunkSize; j++) {
                sublist.add(current++);
            }
            result.add(sublist);
        }
        return result;
    }
}
