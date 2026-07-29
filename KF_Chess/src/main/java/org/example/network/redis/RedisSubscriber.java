package org.example.network.redis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.database.RedisManager;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import java.util.function.BiConsumer;

@Slf4j
public class RedisSubscriber {

    private final ObjectMapper objectMapper;
    private final BiConsumer<String, String> messageHandler; // handler: (roomId, rawJson) -> Void

    public RedisSubscriber(ObjectMapper objectMapper, BiConsumer<String, String> messageHandler) {
        this.objectMapper = objectMapper;
        this.messageHandler = messageHandler;
    }

    public void startListening() {
        Thread listenerThread = new Thread(() -> {
            log.info("[Redis Sub] Starting Redis Pub/Sub subscriber thread...");
            try (Jedis jedis = RedisManager.getResource()) {
                jedis.subscribe(new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {
                        try {
                            JsonNode json = objectMapper.readTree(message);
                            String roomId = json.has("roomId") ? json.get("roomId").asText() : null;

                            if (roomId != null) {
                                messageHandler.accept(roomId, message);
                            }
                        } catch (Exception e) {
                            log.error("[Redis Sub Error] Failed to process incoming Redis message: {}", e.getMessage());
                        }
                    }
                }, RedisPublisher.GAME_EVENTS_CHANNEL);
            } catch (Exception e) {
                log.error("[Redis Sub Fatal Error] Redis subscriber loop crashed: {}", e.getMessage(), e);
            }
        }, "redis-pubsub-listener");

        listenerThread.setDaemon(true);
        listenerThread.start();
    }
}