package org.example.network.redis;

import lombok.extern.slf4j.Slf4j;
import org.example.database.RedisManager;
import redis.clients.jedis.Jedis;

@Slf4j
public class RedisPublisher {

    public static final String GAME_EVENTS_CHANNEL = "game:events";

    public static void publishEvent(String message) {
        try (Jedis jedis = RedisManager.getResource()) {
            jedis.publish(GAME_EVENTS_CHANNEL, message);
            log.debug("[Redis Pub] Published message to channel {}: {}", GAME_EVENTS_CHANNEL, message);
        } catch (Exception e) {
            log.error("[Redis Pub Error] Failed to publish message: {}", e.getMessage(), e);
        }
    }
}