package org.example.network.server.room;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.database.RedisManager;
import org.example.network.server.connection.NatsWebSocketSessionAdapter;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.resps.Tuple;

import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Component
public class MatchmakingManager {

    private final int RANGE_ELO = 100;
    private final long MINUTE = 60 * 1000;
    private static final String QUEUE_KEY = "matchmaking:queue";

    // username -> "sessionId|joinTimeMs". Lets ANY instance's processQueue
    // tick reconstruct a QueueEntry for a player who joined the queue on a
    // different instance -- without this, localEntries (in-process only)
    // meant a player added on shard A was invisible to shard B's tick,
    // so it could never match them (or evict them on timeout).
    private static final String QUEUE_META_KEY = "matchmaking:queue:meta";

    private static class QueueEntry {
        final WebSocketSession session;
        final String username;
        final int rating;
        final long joinTimeMs;

        QueueEntry(WebSocketSession session, String username, int rating, long joinTimeMs) {
            this.session = session;
            this.username = username;
            this.rating = rating;
            this.joinTimeMs = joinTimeMs;
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

        long joinTimeMs = System.currentTimeMillis();
        QueueEntry entry = new QueueEntry(session, username, rating, joinTimeMs);
        localEntries.put(username, entry);

        try (Jedis jedis = RedisManager.getResource()) {
            jedis.zadd(QUEUE_KEY, rating, username);
            jedis.hset(QUEUE_META_KEY, username, session.getId() + "|" + joinTimeMs);
            log.info("[SERVER OUT] User {} ({} ELO) entered matchmaking queue.", username, rating);
        } catch (Exception e) {
            log.error("[SERVER ERROR] Failed to add user {} to Redis matchmaking queue: {}", username, e.getMessage());
        }

        sendMessage(session, "{\"type\":\"MATCHMAKING_STARTED\",\"message\":\"Searching for an opponent (\u00b1100 ELO)...\"}");
    }

    public synchronized void removeFromQueue(WebSocketSession session) {
        String usernameToRemove = null;
        for (Map.Entry<String, QueueEntry> entry : localEntries.entrySet()) {
            // Compare by id, not object identity: virtual (NATS-backed)
            // sessions are re-created per request, so a CANCEL_MATCHMAKING
            // request builds a *different* adapter instance than the one
            // stored during FIND_MATCH, even though it's the same client.
            if (entry.getValue().session.getId().equals(session.getId())) {
                usernameToRemove = entry.getKey();
                break;
            }
        }

        // Fall back to Redis meta in case this instance never held the
        // entry locally (e.g. CANCEL arrives on a different instance than
        // the FIND_MATCH that queued it).
        if (usernameToRemove == null) {
            usernameToRemove = findUsernameBySessionIdInRedis(session.getId());
        }

        if (usernameToRemove != null) {
            localEntries.remove(usernameToRemove);
            try (Jedis jedis = RedisManager.getResource()) {
                jedis.zrem(QUEUE_KEY, usernameToRemove);
                jedis.hdel(QUEUE_META_KEY, usernameToRemove);
                log.info("[SERVER OUT] User {} removed from matchmaking queue.", usernameToRemove);
            } catch (Exception e) {
                log.error("[SERVER ERROR] Failed to remove user {} from Redis matchmaking queue: {}", usernameToRemove, e.getMessage());
            }
        }
    }

    private String findUsernameBySessionIdInRedis(String sessionId) {
        try (Jedis jedis = RedisManager.getResource()) {
            Map<String, String> meta = jedis.hgetAll(QUEUE_META_KEY);
            for (Map.Entry<String, String> e : meta.entrySet()) {
                String[] parts = e.getValue().split("\\|", 2);
                if (parts.length == 2 && parts[0].equals(sessionId)) {
                    return e.getKey();
                }
            }
        } catch (Exception e) {
            log.error("[SERVER ERROR] Failed to look up session {} in matchmaking meta: {}", sessionId, e.getMessage());
        }
        return null;
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
                QueueEntry entry = resolveEntry(jedis, username, (int) tuple.getScore());
                if (entry != null) {
                    queue.add(entry);
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
                jedis.hdel(QUEUE_META_KEY, toRemoveFromRedis.toArray(new String[0]));
            }

        } catch (Exception e) {
            log.error("[SERVER ERROR] Error processing matchmaking queue in Redis: {}", e.getMessage());
        }
    }

    /** Local cache first; reconstructs from the Redis meta hash for entries this instance never saw. */
    private QueueEntry resolveEntry(Jedis jedis, String username, int rating) {
        QueueEntry local = localEntries.get(username);
        if (local != null) return local;

        String meta = jedis.hget(QUEUE_META_KEY, username);
        if (meta == null) return null; // orphaned zset entry with no meta -- nothing safe to do with it

        String[] parts = meta.split("\\|", 2);
        if (parts.length != 2) return null;

        String sessionId = parts[0];
        long joinTimeMs;
        try {
            joinTimeMs = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }

        return new QueueEntry(new NatsWebSocketSessionAdapter(sessionId), username, rating, joinTimeMs);
    }

    private void createMatch(QueueEntry p1, QueueEntry p2) {
        String matchRoomId = "match_" + UUID.randomUUID().toString().substring(0, 8);
        log.info("[SERVER OUT] Match found! Room: {} | {} vs {}", matchRoomId, p1.username, p2.username);

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