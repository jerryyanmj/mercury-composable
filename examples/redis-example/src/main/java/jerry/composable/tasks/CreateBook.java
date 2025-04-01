package jerry.composable.tasks;

import jerry.composable.models.Book;
import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.TypedLambdaFunction;
import org.platformlambda.core.util.Utility;
import jerry.composable.redis.CacheUtility;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

@PreLoad(route="v1.create.book", instances=100)
public class CreateBook implements TypedLambdaFunction<Book, Book> {

    private static final Utility util = Utility.getInstance();
    private static final String PROTECTED_FIELDS = "protected_fields";

    @Override
    public Book handleEvent(Map<String, String> headers, Book book, int instance) throws IOException {
        CacheUtility.put("test-create-book-v1", book, Duration.ofMinutes(5).getSeconds());
        return book;
    }

}
