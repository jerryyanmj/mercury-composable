package jerry.composable.redis.service;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jerry.composable.redis.RedisClientManager;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.platformlambda.core.models.EventEnvelope;
import redis.embedded.RedisServer;

import java.util.HashMap;
import java.util.Map;

public class PutToRedisCacheTests {
    private RedisServer redisServer;
    @Before
    public void setup() throws Exception {
        redisServer = new RedisServer(6379);
    }

    @After
    public void tearDown() throws Exception {
        redisServer.stop();
    }

    @Test
    public void testSuccessfulPut() throws Exception {
        PutToRedisCache putToRedisCache = new PutToRedisCache();
        Map<String, String> headers = Map.of(PutToRedisCache.CACHE_KEY, "test-key");

        Map<String, Object> body = new HashMap<>();
        EventEnvelope data = new EventEnvelope();
        data.setBody("test");
        body.put(PutToRedisCache.CACHE_ITEM, data.toBytes());
        body.put(PutToRedisCache.CACHE_TTL, 90);

        putToRedisCache.handleEvent(headers, body, 0);

        RedisClient r = RedisClientManager.getInstance().getRedisClient();
        RedisCodec<String, byte[]> codec = RedisCodec.of(new StringCodec(), new ByteArrayCodec());
        StatefulRedisConnection<String, byte[]> connection = r.connect(codec);
        RedisAsyncCommands<String, byte[]> asyncCommands = connection.async();

        RedisFuture<byte[]> queryData = asyncCommands.get("test-key");
        Assert.assertNotNull(queryData.get());
    }


    @Test(expected = RuntimeException.class)
    public void testFailingPut() throws Exception {
        PutToRedisCache putToRedisCache = new PutToRedisCache();
        Map<String, String> headers = Map.of(PutToRedisCache.CACHE_KEY, "test-key");

        Map<String, Object> body = new HashMap<>();
        EventEnvelope data = new EventEnvelope();
        data.setBody("test");
        body.put(PutToRedisCache.CACHE_ITEM, data.toBytes());
        body.put(PutToRedisCache.CACHE_TTL, -1);

        putToRedisCache.handleEvent(headers, body, 0);
    }
}
