package org.example.network;

import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns every cross-room/cross-session lookup the WebSocket layer needs:
 * which rooms exist, which room a session or logged-in username belongs to,
 * and which PlayerInfo a session is currently playing as.
 *
 * Previously these four maps were held by ChessWebSocketHandler and passed
 * as separate parameters into every AuthHandler/MessageHandler method. That
 * meant (a) every method signature grew whenever a lookup was needed, and
 * (b) the "wire up room cleanup on end" logic was duplicated in two
 * different places that created rooms. This class fixes both: it's the one
 * place that creates rooms and the one place that cleans them up.
 */
public class RoomRegistry {

    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    // Tracks which room a *participant* (not spectator) is currently in, so
    // a dropped connection can find its way back via RECONNECT/LOGIN.
    // Entries are cleaned up automatically once a room's game ends.
    private final Map<String, GameRoom> usernameToRoom = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, GameRoom> sessionToRoom = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, PlayerInfo> players = new ConcurrentHashMap<>();

    public GameRoom getRoom(String roomId) {
        return rooms.get(roomId);
    }

    /** Atomically creates a brand-new room, or returns null if roomId is already taken. */
    public GameRoom tryCreateRoom(String roomId) {
        GameRoom room = new GameRoom(roomId);
        if (rooms.putIfAbsent(roomId, room) != null) {
            return null; // someone beat us to it; discard the throwaway room
        }
        wireCleanupOnEnd(room);
        return room;
    }

    /** Gets the room for matchmaking, creating it atomically on first arrival. */
    public GameRoom getOrCreateRoom(String roomId) {
        return rooms.computeIfAbsent(roomId, id -> {
            GameRoom room = new GameRoom(id);
            wireCleanupOnEnd(room);
            return room;
        });
    }

    private void wireCleanupOnEnd(GameRoom room) {
        room.setOnEnded(() -> {
            rooms.remove(room.getRoomId());
            usernameToRoom.entrySet().removeIf(e -> e.getValue() == room);
        });
    }

    /** The room a username is currently an active (non-ended) participant of, or null. */
    public GameRoom getActiveRoomForUsername(String username) {
        GameRoom room = usernameToRoom.get(username);
        return (room == null || room.isEnded()) ? null : room;
    }

    public void bindParticipant(WebSocketSession session, String username, GameRoom room, char color) {
        players.put(session, new PlayerInfo(username, color));
        sessionToRoom.put(session, room);
        usernameToRoom.put(username, room);
    }

    public void bindSpectator(WebSocketSession session, String username, GameRoom room) {
        players.put(session, new PlayerInfo(username, '-'));
        sessionToRoom.put(session, room);
    }

    /** Records identity/color for a session that isn't bound to a room yet. */
    public void registerPlayerInfo(WebSocketSession session, String username, char color) {
        players.put(session, new PlayerInfo(username, color));
    }

    public PlayerInfo getPlayer(WebSocketSession session) {
        return players.get(session);
    }

    public GameRoom getRoomForSession(WebSocketSession session) {
        return sessionToRoom.get(session);
    }

    /** Cleans up all per-session bookkeeping for a closed connection and returns its room, if any. */
    public GameRoom dropSession(WebSocketSession session) {
        players.remove(session);
        return sessionToRoom.remove(session);
    }

    /** Tears down bookkeeping for a room nobody ever showed up to play in. */
    public void unregisterRoomIfAbandoned(GameRoom room) {
        rooms.remove(room.getRoomId());
        usernameToRoom.entrySet().removeIf(e -> e.getValue() == room);
    }
}
