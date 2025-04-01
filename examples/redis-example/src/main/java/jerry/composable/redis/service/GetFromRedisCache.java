package jerry.composable.redis.service;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.TypedLambdaFunction;
import jerry.composable.redis.RedisClientManager;

import java.util.Map;
import java.util.concurrent.ExecutionException;

import static jerry.composable.redis.service.GetFromRedisCache.ROUTE;

@PreLoad(route = ROUTE)
public class GetFromRedisCache implements TypedLambdaFunction<Map<String, Object>, byte[]> {

    final public static String ROUTE = "cache.redis.v1.get";
    final public static String CACHE_KEY = "cache_key";

    @Override
    public byte[] handleEvent(Map<String, String> headers, Map<String, Object> input, int instance) {

        String key = headers.get(CACHE_KEY);

        RedisClient r = RedisClientManager.getInstance().getRedisClient();
        RedisCodec<String, byte[]> codec = RedisCodec.of(new StringCodec(), new ByteArrayCodec());

        StatefulRedisConnection<String, byte[]> connection = r.connect(codec);
        try {
            RedisAsyncCommands<String, byte[]> rCommands = connection.async();

            RedisFuture<byte[]> result = rCommands.get(key);

            if (result.getError() != null) {
               throw new RuntimeException(result.getError());
            }
            return result.get();
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            connection.closeAsync();
        }
    }
}
