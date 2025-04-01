package jerry.composable.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import org.platformlambda.core.util.AppConfigReader;

public class RedisClientManager {

    public static final String REDIS_HOST = "redis.host";
    public static final String REDIS_PORT = "redis.port";
    public static final String REDIS_SSL = "redis.ssl";
    public static final String REDIS_DATABASE = "redis.database";

    private static RedisClientManager redisClientManager;
    private final RedisClient redisClient;

    private RedisClientManager() {
        redisClient = RedisClient.create(getRedisConfig());
    }

    public synchronized static RedisClientManager getInstance() {
        if (redisClientManager == null) {
            redisClientManager = new RedisClientManager();
        }

        return redisClientManager;
    }

    public RedisClient getRedisClient() {
        return redisClient;
    }

    public void closeClient() {
        if (redisClient != null) {
            redisClient.shutdown();
        }
    }

    private RedisURI getRedisConfig() {
        AppConfigReader a = AppConfigReader.getInstance();
        String host = a.getProperty(REDIS_HOST, "localhost");
        int port = Integer.parseInt(a.getProperty(REDIS_PORT, "6379"));
        boolean ssl = Boolean.parseBoolean(a.getProperty(REDIS_SSL, "false"));
        int database =Integer.parseInt(a.getProperty(REDIS_DATABASE, "0"));
        RedisURI.Builder builder =  RedisURI.Builder.redis(host)
                .withPort(port)
                .withDatabase(database)
                .withSsl(ssl);

        return builder.build();
    }
}
