package jerry.composable.redis;

import jerry.composable.redis.service.DeleteFromRedisCache;
import jerry.composable.redis.service.GetFromRedisCache;
import org.platformlambda.core.models.EventEnvelope;
import org.platformlambda.core.system.PostOffice;
import org.platformlambda.core.util.Utility;
import org.springframework.util.SerializationUtils;
import jerry.composable.redis.service.PutToRedisCache;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class CacheUtility {

    public static void putWithEnvelope(String key, EventEnvelope data, long ttl) throws IOException {
        String traceId = Utility.getInstance().getUuid();
        PostOffice po = new PostOffice("cache.utility.put", traceId, "cache.utility.put");
        byte[] itemToCache = data.toBytes();
        EventEnvelope event = new EventEnvelope();
        event.setTo(PutToRedisCache.ROUTE);
        event.setHeader(PutToRedisCache.CACHE_KEY, key);
        Map<String, Object> body = new HashMap<>();
        body.put(PutToRedisCache.CACHE_TTL, ttl);
        body.put(PutToRedisCache.CACHE_ITEM, itemToCache);
        event.setBody(body);
        po.send(event);
    }

    public static Optional<EventEnvelope> getWithEnvelope(String key) throws IOException, ExecutionException, InterruptedException {
        String traceId = Utility.getInstance().getUuid();
        PostOffice po = new PostOffice("cache.utility.getWithEnvelope", traceId, "cache.utility.getWithEnvelope");

        EventEnvelope event = new EventEnvelope();
        event.setTo(GetFromRedisCache.ROUTE);
        event.setHeader(GetFromRedisCache.CACHE_KEY, key);

        Future<EventEnvelope> r = po.request(event, 100000);
        EventEnvelope result = r.get();
        if (result.hasError()) {
            return Optional.empty();
        }
        byte[] obj = (byte[]) result.getBody();
        EventEnvelope data = new EventEnvelope(obj);
        return Optional.of(data);
    }

    public static <T> void put(String key, T value, long ttl) throws IOException {
        String traceId = Utility.getInstance().getUuid();
        PostOffice po = new PostOffice("cache.utility.put", traceId, "cache.utility.put");
        EventEnvelope data = new EventEnvelope();
        data.setBody(value);
//        byte[] itemToCache = SerializationUtils.serialize(value);
        EventEnvelope event = new EventEnvelope();
        event.setTo(PutToRedisCache.ROUTE);
        event.setHeader(PutToRedisCache.CACHE_KEY, key);
        Map<String, Object> body = new HashMap<>();
        body.put(PutToRedisCache.CACHE_TTL, ttl);
        body.put(PutToRedisCache.CACHE_ITEM, data.toBytes());
        event.setBody(body);
        po.send(event);
    }

    public static <T> Optional<T> get(String key, Class<T> clazz) throws Exception {
        String traceId = Utility.getInstance().getUuid();
        PostOffice po = new PostOffice("cache.utility.get", traceId, "cache.utility.get");

        EventEnvelope event = new EventEnvelope();
        event.setTo(GetFromRedisCache.ROUTE);
        event.setHeader(GetFromRedisCache.CACHE_KEY, key);

        Future<EventEnvelope> r = po.request(event, 100000);
        EventEnvelope result = r.get();
        if (result.hasError() || result.getBody() == null) {
            return Optional.empty();
        }
        byte[] obj = (byte[]) result.getBody();
        EventEnvelope data = new EventEnvelope(obj);
        return Optional.of(data.getBody(clazz));
    }

    public static boolean delete(String key) throws Exception {
        String traceId = Utility.getInstance().getUuid();
        PostOffice po = new PostOffice("cache.utility.delete", traceId, "cache.utility.delete");

        EventEnvelope event = new EventEnvelope();
        event.setTo(DeleteFromRedisCache.ROUTE);
        event.setHeader(DeleteFromRedisCache.CACHE_KEY, key);

        Future<EventEnvelope> r = po.request(event, 100000);
        EventEnvelope result = r.get();
        if (result.hasError()) {
            throw new Exception(result.getError());
        }
        return true;
    }
}
