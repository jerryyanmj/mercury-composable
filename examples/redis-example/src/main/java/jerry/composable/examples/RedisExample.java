package jerry.composable.examples;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.reactive.RedisReactiveCommands;
import io.lettuce.core.api.sync.RedisCommands;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public class RedisExample {

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        RedisClient r = RedisClient.create("redis://localhost:6379");
        StatefulRedisConnection<String, String> rConn = r.connect();

        RedisCommands<String, String> syncCommands = rConn.sync();

        String key = "key";
        String value = "value";

        syncCommands.set(key, value);
        String response = syncCommands.get(key);

        System.out.println(response);

        syncCommands.hset("Person", "name", "Jerry");
        syncCommands.hset("Person", "age", "42");

        Map<String, String> person = syncCommands.hgetall("Person");

        System.out.println(person.get("name") + ":" + person.get("age"));

        RedisAsyncCommands<String, String> asyncCommands = rConn.async();



        asyncCommands.hset("Person2", "name", "Jerry Yan");
        asyncCommands.hset("Person2", "age", "42");

        RedisFuture<Map<String, String>> redisFuture = asyncCommands.hgetall("Person2");

        System.out.println(redisFuture.get().get("name") + ":" + redisFuture.get().get("age"));

        System.out.println("Work with objects");

        RedisReactiveCommands<String, String> reactiveCommands = rConn.reactive();

        reactiveCommands.set("key", "value").subscribe(System.out::println);

        reactiveCommands.get("nothing")
                .defaultIfEmpty("NOTHING")
                .flatMap(s -> {
                            System.out.println("Check null");
                            return Mono.just("NOTHING".equals(s));
                })
                .flatMap(aBoolean -> {
                    System.out.println("Do switch");
                    return aBoolean
                            ? Mono.error(new RuntimeException("Not found"))
                            : Mono.just("HasValue");
                })
                .doFinally(signal -> System.out.println("Do finally"))
                .subscribe(System.out::println);
        Thread.sleep(5000);
        rConn.close();

        byte[] test = new byte[0];
        System.out.println("".getBytes().length);
        System.out.println(test.length);

        r.shutdown();
    }
}
