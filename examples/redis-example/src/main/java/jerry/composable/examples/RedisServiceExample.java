package jerry.composable.examples;

import jerry.composable.models.Book;
import jerry.composable.redis.CacheUtility;

public class RedisServiceExample {

    public static void main(String[] args) throws Exception {

        Book b = new Book(100L, "My story");


        Thread.sleep(3000);
    }

}
