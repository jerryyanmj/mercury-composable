/*

    Copyright 2018-2024 Accenture Technology

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

 */

 package jerry.composable.tasks;

import jerry.composable.models.Book;
import jerry.composable.redis.CacheUtility;
import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.TypedLambdaFunction;
import org.platformlambda.core.util.Utility;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@PreLoad(route="v1.delete.book", instances=100)
public class DeleteBook implements TypedLambdaFunction<Map<String, Object>, Map<String, Object>> {

    private static final  Utility util = Utility.getInstance();

    private static final String BOOK_KEY = "book_key";
    private static final String DELETED = "DELETED";

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers, Map<String, Object> input, int instance)
            throws Exception {

        String bookKey = headers.get(BOOK_KEY);
        Map<String, Object> result = new HashMap<>();
        result.put(BOOK_KEY, bookKey);
        result.put(DELETED, CacheUtility.delete(bookKey));
        return result;
    }

}
