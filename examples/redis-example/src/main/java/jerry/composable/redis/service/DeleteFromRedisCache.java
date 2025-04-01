package jerry.composable.redis.service;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jerry.composable.redis.RedisClientManager;
import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.TypedLambdaFunction;

import java.util.Map;

import static jerry.composable.redis.service.DeleteFromRedisCache.ROUTE;

@PreLoad(route = ROUTE)
public class DeleteFromRedisCache implements TypedLambdaFunction<Map<String, Object>, Void> {

    final public static String ROUTE = "cache.redis.v1.delete";
    final public static String CACHE_KEY = "cache_key";

    @Override
    public Void handleEvent(Map<String, String> headers, Map<String, Object> input, int instance) throws Exception {

        String key = headers.get(CACHE_KEY);

        RedisClient r = RedisClientManager.getInstance().getRedisClient();
        RedisCodec<String, byte[]> codec = RedisCodec.of(new StringCodec(), new ByteArrayCodec());

        StatefulRedisConnection<String, byte[]> connection = r.connect(codec);
        try {
            RedisAsyncCommands<String, byte[]> rCommands = connection.async();

            RedisFuture<Long> result = rCommands.del(key);

            if (result.getError() != null) {
               throw new Exception(result.getError());
            }
            return null;
        } finally {
            connection.closeAsync();
        }
    }
}
