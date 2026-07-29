package org.example.database;

import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@Slf4j
public class RedisManager {

    private static final String REDIS_HOST = "localhost";
    private static final int REDIS_PORT = 6379;

    private static final JedisPool pool;

    static {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(32);
        poolConfig.setMaxIdle(16);
        poolConfig.setMinIdle(4);

        pool = new JedisPool(poolConfig, REDIS_HOST, REDIS_PORT);
        log.info("[Redis] Connection pool initialized successfully on {}:{}", REDIS_HOST, REDIS_PORT);
    }

    public static Jedis getResource() {
        return pool.getResource();
    }

    public static JedisPool getPool() {
        return pool;
    }
}