package org.example.database;

import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;

/**
 * What changed and why:
 * <ul>
 *   <li>Host/port were hardcoded to {@code localhost:6379} -- same problem
 *       as {@code DatabaseManager}'s old hardcoded credentials: no way to
 *       point at a different Redis in Docker/K8s without a rebuild. Now
 *       env-configurable, matching {@code NatsBridge}'s pattern.</li>
 *   <li>The pool had no {@code maxWait}. A Redis node that's merely slow
 *       (not fully down) could hand out a connection after an unbounded
 *       wait, or a caller could block indefinitely trying to borrow one --
 *       dangerous specifically because {@code MatchmakingManager} runs its
 *       queue processing on a single scheduled thread, so one stuck Redis
 *       call stalls matchmaking for every waiting player. Bounded to 2s.</li>
 *   <li>Added {@code shutdown()} so the pool's connections are released
 *       cleanly on graceful process shutdown instead of leaking.</li>
 * </ul>
 */
@Slf4j
public class RedisManager {

    private static final String REDIS_HOST = System.getenv().getOrDefault("REDIS_HOST", "localhost");
    private static final int REDIS_PORT = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));

    private static final JedisPool pool;

    static {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(32);
        poolConfig.setMaxIdle(16);
        poolConfig.setMinIdle(4);
        poolConfig.setMaxWait(Duration.ofSeconds(2));

        pool = new JedisPool(poolConfig, REDIS_HOST, REDIS_PORT);
        log.info("[Redis] Connection pool initialized successfully on {}:{}", REDIS_HOST, REDIS_PORT);
    }

    public static Jedis getResource() {
        return pool.getResource();
    }

    public static JedisPool getPool() {
        return pool;
    }

    /** Call on graceful shutdown so the pool doesn't leak connections. */
    public static void shutdown() {
        pool.close();
    }
}
