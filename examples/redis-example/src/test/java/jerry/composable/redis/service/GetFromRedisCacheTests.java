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

public class GetFromRedisCacheTests {
    private RedisServer redisServer;
    @Before
    public void setup() throws Exception {
        redisServer = new RedisServer(6379);

        RedisClient r = RedisClientManager.getInstance().getRedisClient();
        RedisCodec<String, byte[]> codec = RedisCodec.of(new StringCodec(), new ByteArrayCodec());
        StatefulRedisConnection<String, byte[]> connection = r.connect(codec);
        RedisAsyncCommands<String, byte[]> asyncCommands = connection.async();

        EventEnvelope data = new EventEnvelope();
        data.setBody("test");
        RedisFuture<String> result = asyncCommands.set("test-key", data.toBytes());
        result.get();
    }

    @After
    public void tearDown() throws Exception {
        redisServer.stop();
    }

    @Test
    public void testSuccessfulGet() throws Exception {
        GetFromRedisCache getFromRedisCache = new GetFromRedisCache();
        Map<String, String> headers = Map.of(PutToRedisCache.CACHE_KEY, "test-key");
        byte[] getResult = getFromRedisCache.handleEvent(headers, Map.of(), 0);
        EventEnvelope getResultData = new EventEnvelope(getResult);
        Assert.assertEquals("test", getResultData.getBody(String.class));
    }

    @Test
    public void testFailingGet() throws Exception {
        GetFromRedisCache getFromRedisCache = new GetFromRedisCache();
        Map<String, String> headers = Map.of(PutToRedisCache.CACHE_KEY, "non-exist-test-key");
        byte[] getResult = getFromRedisCache.handleEvent(headers, Map.of(), 0);
        Assert.assertNull(getResult);
    }
}
