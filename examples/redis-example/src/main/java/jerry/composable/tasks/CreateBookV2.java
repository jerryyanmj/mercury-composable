package jerry.composable.tasks;

import jerry.composable.models.Book;
import jerry.composable.redis.CacheUtility;
import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.EventEnvelope;
import org.platformlambda.core.models.TypedLambdaFunction;
import org.platformlambda.core.util.Utility;
import org.springframework.util.SerializationUtils;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@PreLoad(route="v2.create.book", instances=10)
public class CreateBookV2 implements TypedLambdaFunction<Book, Map<String, Object>> {

    @Override
    public Map<String, Object> handleEvent(Map<String, String> headers, Book book, int instance) throws IOException {
        book.setName(book.getName() + book.getName());

        EventEnvelope data = new EventEnvelope();
        data.setBody(book);
        Map<String, Object> result = new HashMap<>();
        result.put("cache_item", data.toBytes());
        result.put("book", book);
        return result;
    }

}
