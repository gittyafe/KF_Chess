package org.example.network.server.room;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.database.RedisManager;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.resps.Tuple;

import java.util.*;
import java.util.concurrent.*;

@Slf4j
public class MatchmakingManager {

    private final int RANGE_ELO = 100;
    private final long MINUTE = 60 * 1000;
    private static final String QUEUE_KEY = "matchmaking:queue";

    private static class QueueEntry {
        final WebSocketSession session;
        final String username;
        final int rating;
        final long joinTimeMs;

        QueueEntry(WebSocketSession session, String username, int rating) {
            this.session = session;
            this.username = username;
            this.rating = rating;
            this.joinTimeMs = System.currentTimeMillis();
        }
    }

    private final Map<String, QueueEntry> localEntries = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ObjectMapper objectMapper;

    public MatchmakingManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.scheduler.scheduleAtFixedRate(this::processQueue, 2, 2, TimeUnit.SECONDS);
    }

    public synchronized void addToQueue(WebSocketSession session, String username, int rating) {
        removeFromQueue(session);

        QueueEntry entry = new QueueEntry(session, username, rating);
        localEntries.put(username, entry);

        try (Jedis jedis = RedisManager.getResource()) {
            jedis.zadd(QUEUE_KEY, rating, username);
            log.info("[SERVER OUT] User {} ({} ELO) entered matchmaking queue.", username, rating);
        } catch (Exception e) {
            log.error("[SERVER ERROR] Failed to add user {} to Redis matchmaking queue: {}", username, e.getMessage());
        }

        sendMessage(session, "{\"type\":\"MATCHMAKING_STARTED\",\"message\":\"Searching for an opponent (\u00b1100 ELO)...\"}");
    }

    public synchronized void removeFromQueue(WebSocketSession session) {
        String usernameToRemove = null;
        for (Map.Entry<String, QueueEntry> entry : localEntries.entrySet()) {
            if (entry.getValue().session.equals(session)) {
                usernameToRemove = entry.getKey();
                break;
            }
        }

        if (usernameToRemove != null) {
            localEntries.remove(usernameToRemove);
            try (Jedis jedis = RedisManager.getResource()) {
                jedis.zrem(QUEUE_KEY, usernameToRemove);
            } catch (Exception e) {
                log.error("[SERVER ERROR] Failed to remove user {} from Redis matchmaking queue: {}", usernameToRemove, e.getMessage());
            }
        }
    }

    private synchronized void processQueue() {
        long currentTime = System.currentTimeMillis();
        Set<String> toRemoveFromRedis = new HashSet<>();

        try (Jedis jedis = RedisManager.getResource()) {
            List<Tuple> redisEntries = jedis.zrangeWithScores(QUEUE_KEY, 0, -1);
            if (redisEntries.isEmpty()) return;

            List<QueueEntry> queue = new ArrayList<>();
            for (Tuple tuple : redisEntries) {
                String username = tuple.getElement();
                QueueEntry localEntry = localEntries.get(username);
                if (localEntry != null) {
                    queue.add(localEntry);
                }
            }

            for (int i = 0; i < queue.size(); i++) {
                QueueEntry player1 = queue.get(i);
                if (toRemoveFromRedis.contains(player1.username)) continue;

                if (currentTime - player1.joinTimeMs > MINUTE) {
                    sendMessage(player1.session, "{\"type\":\"MATCHMAKING_TIMEOUT\",\"reason\":\"No suitable opponent found within 60 seconds.\"}");
                    toRemoveFromRedis.add(player1.username);
                    localEntries.remove(player1.username);
                    continue;
                }

                for (int j = i + 1; j < queue.size(); j++) {
                    QueueEntry player2 = queue.get(j);
                    if (toRemoveFromRedis.contains(player2.username)) continue;

                    if (Math.abs(player1.rating - player2.rating) <= RANGE_ELO) {
                        long removed = jedis.zrem(QUEUE_KEY, player1.username, player2.username);
                        if (removed == 2) {
                            createMatch(player1, player2);
                            toRemoveFromRedis.add(player1.username);
                            toRemoveFromRedis.add(player2.username);
                            localEntries.remove(player1.username);
                            localEntries.remove(player2.username);
                            break;
                        }
                    }
                }
            }

            if (!toRemoveFromRedis.isEmpty()) {
                jedis.zrem(QUEUE_KEY, toRemoveFromRedis.toArray(new String[0]));
            }

        } catch (Exception e) {
            log.error("[SERVER ERROR] Error processing matchmaking queue in Redis: {}", e.getMessage());
        }
    }

    private void createMatch(QueueEntry p1, QueueEntry p2) {
        String matchRoomId = "match_" + UUID.randomUUID().toString().substring(0, 8);
        System.out.println("Match found! Room: " + matchRoomId + " | " + p1.username + " vs " + p2.username);

        sendMessage(p1.session, String.format("{\"type\":\"MATCH_FOUND\",\"roomId\":\"%s\",\"opponent\":\"%s\"}", matchRoomId, p2.username));
        sendMessage(p2.session, String.format("{\"type\":\"MATCH_FOUND\",\"roomId\":\"%s\",\"opponent\":\"%s\"}", matchRoomId, p1.username));
    }

    private void sendMessage(WebSocketSession session, String text) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(text));
            }
        } catch (Exception e) {
            log.error("[SERVER ERROR] Failed to send matchmaking message: {}", e.getMessage());
        }
    }
}