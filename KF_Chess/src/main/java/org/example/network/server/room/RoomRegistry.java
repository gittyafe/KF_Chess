package org.example.network.server.room;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.database.RedisManager;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;

import java.net.InetAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns every cross-room/cross-session lookup the WebSocket layer needs:
 * which rooms exist, which room a session or logged-in username belongs
 * to, and which {@link PlayerInfo} a session is currently playing as.
 *
 * <p>NOTE: registered as a Spring {@code @Component} so it's a single
 * shared bean -- {@code AuthNatsSubscriber}, {@code
 * GameCommandNatsSubscriber} and {@code NatsGameListener} all take one in
 * their constructor. If a {@code @Bean} for {@code RoomRegistry} already
 * exists elsewhere in the project, remove one of the two to avoid an
 * ambiguous-bean error.
 *
 * <p><b>Why sessions are keyed by String id, not by WebSocketSession
 * object identity, and mirrored to Redis:</b> see the extended
 * explanation kept from the previous pass -- unchanged here.
 *
 * <p><b>Room ownership (new in this pass):</b> {@link #tryCreateRoom}
 * and {@link #getOrCreateRoom} now go through a Redis-backed election
 * (SET-if-not-exists, keyed by roomId, value = this process's {@link
 * #getShardId()}) before creating a {@link GameRoom} in memory. This is
 * the missing "Game Allocator" primitive from {@code Server_Design.md}:
 * it answers "does anyone already own this roomId, and if not, can I
 * claim it?" across however many Game Shard processes are running.
 *
 * <p><b>What this does NOT yet do:</b> if a different shard already owns
 * roomId X and *this* process is asked to seat a player into it (a
 * {@code JOIN_MATCH}/{@code JOIN_ROOM} landing on the "wrong" process),
 * there is currently no mechanism to forward that join across the wire
 * to the owning shard and get a real seat back -- {@code AuthHandler}
 * expects a synchronous, in-process {@link GameRoom} it can call {@code
 * .addPlayer(...)} on directly. {@code getOrCreateRoom} therefore falls
 * back to creating a second, competing, empty {@code GameRoom} locally
 * when it loses the election, logging a loud warning instead of failing
 * silently. That fallback keeps today's (effectively single-shard)
 * behavior working, but it is NOT correct once you actually run more
 * than one Game Shard process: two players matched into the same
 * {@code roomId} could each end up seated into a *different* {@code
 * GameRoom} instance, on different processes, each blind to the other.
 * Closing that gap for real requires turning {@code GameRoom} into an
 * interface with a "local" and a "remote/NATS-proxy" implementation (or
 * equivalent), so join operations become request/reply over NATS when
 * the target room isn't local -- a genuine cross-cutting change to
 * {@code AuthHandler}, {@code MessageHandler}, and {@code
 * MatchmakingManager} as well, not just this class. Flagging it clearly
 * rather than guessing at that redesign unilaterally.
 */
@Slf4j
@Component
public class RoomRegistry {

    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();

    // Tracks which room a *participant* (not spectator) is currently in, so
    // a dropped connection can find its way back via RECONNECT/LOGIN.
    // Entries are cleaned up automatically once a room's game ends.
    private final Map<String, GameRoom> usernameToRoom = new ConcurrentHashMap<>();

    private final Map<String, GameRoom> sessionIdToRoom = new ConcurrentHashMap<>();
    private final Map<String, PlayerInfo> sessionIdToPlayer = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SESSION_KEY_PREFIX = "kfchess:session:"; // + sessionId -> JSON SessionRecord
    private static final int SESSION_TTL_SECONDS = 6 * 60 * 60; // safety net; also actively deleted on dropSession

    private static final String ROOM_OWNER_KEY_PREFIX = "kfchess:room-owner:"; // + roomId -> shardId
    // Games last 30-90s per Server_Design.md; this TTL is a large safety
    // margin so a crashed shard's claim self-heals within an hour even
    // without an active heartbeat/renewal (deliberately not wired into the
    // tick loop -- see class docs).
    private static final int ROOM_OWNER_TTL_SECONDS = 60 * 60;

    /** Stable per-process identity used as the value of a room-ownership claim. */
    private static final String SHARD_ID = System.getenv().getOrDefault("SHARD_ID", generateShardId());

    private static String generateShardId() {
        try {
            return InetAddress.getLocalHost().getHostName() + "-" + UUID.randomUUID().toString().substring(0, 8);
        } catch (Exception e) {
            return "shard-" + UUID.randomUUID();
        }
    }

    public static String getShardId() {
        return SHARD_ID;
    }

    /** Plain class (not a record) on purpose -- Jackson needs a no-arg
     *  constructor + public fields here without relying on the
     *  {@code -parameters} compiler flag being set for record support. */
    private static class SessionRecord {
        public String username;
        public char color;
        public String roomId; // nullable: set only once the session is seated in a room

        public SessionRecord() {
        }

        public SessionRecord(String username, char color, String roomId) {
            this.username = username;
            this.color = color;
            this.roomId = roomId;
        }
    }

    public GameRoom getRoom(String roomId) {
        return rooms.get(roomId);
    }

    /** Atomically creates a brand-new room, or returns null if roomId is already taken (locally, or by another shard). */
    public GameRoom tryCreateRoom(String roomId) {
        if (rooms.containsKey(roomId)) {
            return null;
        }
        if (!claimOwnership(roomId)) {
            log.info("[ROOM REGISTRY] Room [{}] is already owned (by shard [{}]); refusing to create a duplicate.",
                    roomId, getOwningShardId(roomId));
            return null;
        }

        GameRoom room = new GameRoom(roomId);
        if (rooms.putIfAbsent(roomId, room) != null) {
            releaseOwnership(roomId); // lost a local race we didn't expect to be possible; stay defensive anyway
            return null;
        }
        wireCleanupOnEnd(room);
        return room;
    }

    /**
     * Gets the room for matchmaking, creating it atomically on first
     * arrival. See the class-level docs for the current limitation: if
     * another shard already owns {@code roomId}, this still creates a
     * local room rather than failing outright (no cross-shard join
     * forwarding exists yet) -- but it now logs loudly instead of doing
     * so silently.
     */
    public synchronized GameRoom getOrCreateRoom(String roomId) {
        GameRoom existing = rooms.get(roomId);
        if (existing != null) {
            return existing;
        }

        if (!claimOwnership(roomId)) {
            log.warn("[ROOM REGISTRY] Room [{}] is owned by shard [{}], not this one ([{}]). Cross-shard join " +
                    "forwarding isn't implemented yet, so this process will create its own local room anyway -- " +
                    "players matched into this roomId across shards may end up in two different game instances. " +
                    "See RoomRegistry class docs.", roomId, getOwningShardId(roomId), SHARD_ID);
        }

        GameRoom room = new GameRoom(roomId);
        rooms.put(roomId, room);
        wireCleanupOnEnd(room);
        return room;
    }

    private void wireCleanupOnEnd(GameRoom room) {
        room.setOnEnded(() -> {
            rooms.remove(room.getRoomId());
            usernameToRoom.entrySet().removeIf(e -> e.getValue() == room);
            releaseOwnership(room.getRoomId());
        });
    }

    /** The room a username is currently an active (non-ended) participant of, or null. */
    public GameRoom getActiveRoomForUsername(String username) {
        GameRoom room = usernameToRoom.get(username);
        return (room == null || room.isEnded()) ? null : room;
    }

    public void bindParticipant(WebSocketSession session, String username, GameRoom room, char color) {
        String sessionId = session.getId();
        sessionIdToPlayer.put(sessionId, new PlayerInfo(username, color));
        sessionIdToRoom.put(sessionId, room);
        usernameToRoom.put(username, room);
        mirrorToRedis(sessionId, username, color, room.getRoomId());
    }

    public void bindSpectator(WebSocketSession session, String username, GameRoom room) {
        String sessionId = session.getId();
        sessionIdToPlayer.put(sessionId, new PlayerInfo(username, '-'));
        sessionIdToRoom.put(sessionId, room);
        mirrorToRedis(sessionId, username, '-', room.getRoomId());
    }

    /** Records identity/color for a session that isn't bound to a room yet. */
    public void registerPlayerInfo(WebSocketSession session, String username, char color) {
        String sessionId = session.getId();
        sessionIdToPlayer.put(sessionId, new PlayerInfo(username, color));
        mirrorToRedis(sessionId, username, color, null);
    }

    public PlayerInfo getPlayer(WebSocketSession session) {
        return getPlayerBySessionId(session.getId());
    }

    /** Same as {@link #getPlayer}, but usable from code that only has the id (e.g. a NATS handler). */
    public PlayerInfo getPlayerBySessionId(String sessionId) {
        PlayerInfo local = sessionIdToPlayer.get(sessionId);
        if (local != null) return local;

        SessionRecord record = loadFromRedis(sessionId);
        return record == null ? null : new PlayerInfo(record.username, record.color);
    }

    public GameRoom getRoomForSession(WebSocketSession session) {
        return getRoomForSessionId(session.getId());
    }

    /**
     * Resolves the room for a session id, including sessions this process
     * never saw locally (via the Redis mirror). Only ever returns a room
     * this process actually hosts in memory -- if some other shard owns
     * it, that shard will independently resolve the same broadcast NATS
     * message via its own local {@code rooms} map, exactly like {@code
     * NatsGameListener} already does for {@code game.events.*}.
     */
    public GameRoom getRoomForSessionId(String sessionId) {
        GameRoom local = sessionIdToRoom.get(sessionId);
        if (local != null) return local;

        SessionRecord record = loadFromRedis(sessionId);
        if (record == null || record.roomId == null) return null;
        return rooms.get(record.roomId);
    }

    /** Cleans up all per-session bookkeeping for a closed connection and returns its room, if any. */
    public GameRoom dropSession(WebSocketSession session) {
        String sessionId = session.getId();
        sessionIdToPlayer.remove(sessionId);
        removeFromRedis(sessionId);
        return sessionIdToRoom.remove(sessionId);
    }

    /** Tears down bookkeeping for a room nobody ever showed up to play in. */
    public void unregisterRoomIfAbandoned(GameRoom room) {
        rooms.remove(room.getRoomId());
        usernameToRoom.entrySet().removeIf(e -> e.getValue() == room);
        releaseOwnership(room.getRoomId());
    }

    /** Which shard currently owns roomId, per Redis -- null if nobody does (or Redis is unreachable). */
    public String getOwningShardId(String roomId) {
        try (Jedis jedis = RedisManager.getResource()) {
            return jedis.get(ROOM_OWNER_KEY_PREFIX + roomId);
        } catch (Exception e) {
            log.warn("[ROOM REGISTRY] Failed to read owner of room {} from Redis: {}", roomId, e.getMessage());
            return null;
        }
    }

    public boolean isLocallyOwned(String roomId) {
        return SHARD_ID.equals(getOwningShardId(roomId));
    }

    // ---- Room ownership election (Redis-backed) ----

    private boolean claimOwnership(String roomId) {
        try (Jedis jedis = RedisManager.getResource()) {
            SetParams params = SetParams.setParams().nx().ex(ROOM_OWNER_TTL_SECONDS);
            String result = jedis.set(ROOM_OWNER_KEY_PREFIX + roomId, SHARD_ID, params);
            return "OK".equals(result);
        } catch (Exception e) {
            log.warn("[ROOM REGISTRY] Failed to claim ownership of room {} in Redis -- proceeding as sole " +
                    "owner (Redis unavailable: multi-shard coordination degrades to single-shard behavior): {}",
                    roomId, e.getMessage());
            return true; // fail open -- don't block room creation just because Redis is briefly down
        }
    }

    private void releaseOwnership(String roomId) {
        try (Jedis jedis = RedisManager.getResource()) {
            jedis.del(ROOM_OWNER_KEY_PREFIX + roomId);
        } catch (Exception e) {
            log.warn("[ROOM REGISTRY] Failed to release ownership of room {} in Redis (will self-heal via TTL): {}",
                    roomId, e.getMessage());
        }
    }

    // ---- Session mirror (Redis-backed): best-effort only, never allowed to break the local (in-process) path ----

    private void mirrorToRedis(String sessionId, String username, char color, String roomId) {
        try (Jedis jedis = RedisManager.getResource()) {
            SessionRecord record = new SessionRecord(username, color, roomId);
            jedis.setex(SESSION_KEY_PREFIX + sessionId, SESSION_TTL_SECONDS, objectMapper.writeValueAsString(record));
        } catch (Exception e) {
            log.warn("[ROOM REGISTRY] Failed to mirror session {} to Redis -- cross-shard lookups for it " +
                    "(e.g. routing that player's moves) may fail until it's re-bound: {}", sessionId, e.getMessage());
        }
    }

    private SessionRecord loadFromRedis(String sessionId) {
        try (Jedis jedis = RedisManager.getResource()) {
            String json = jedis.get(SESSION_KEY_PREFIX + sessionId);
            return json == null ? null : objectMapper.readValue(json, SessionRecord.class);
        } catch (Exception e) {
            log.warn("[ROOM REGISTRY] Failed to load session {} from Redis: {}", sessionId, e.getMessage());
            return null;
        }
    }

    private void removeFromRedis(String sessionId) {
        try (Jedis jedis = RedisManager.getResource()) {
            jedis.del(SESSION_KEY_PREFIX + sessionId);
        } catch (Exception e) {
            log.warn("[ROOM REGISTRY] Failed to remove session {} from Redis: {}", sessionId, e.getMessage());
        }
    }
}
