package org.example.network.server.game;

import lombok.extern.slf4j.Slf4j;
import org.example.network.nats.NatsBridge;
import org.example.network.server.room.GameRoom;
import org.example.network.server.room.RoomRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/**
 * Closes the last leg of the room-broadcast path. {@code RoomMessenger}
 * publishes every whole-room message (GAME_STARTED, board ticks,
 * MOVE_LOGGED, GAME_OVER, disconnect countdowns -- everything that goes
 * through {@code broadcastRaw}) to {@code "game.updates.<roomId>"}. Until
 * now nothing subscribed to that subject anywhere in the codebase, so
 * every one of those messages was published into a void: a room's two
 * players would get JOIN_ACCEPTED (a single-session send, correctly
 * routed via {@code gateway.outbound.<sessionId>|RoomMessenger.sendTo})
 * and then never hear from the server again -- no GAME_STARTED, no board
 * state, nothing.
 *
 * <p>Every Game Shard runs one instance of this (plain broadcast
 * subscribe, same pattern as {@code GameCommandNatsSubscriber} / {@code
 * NatsGameListener}: every shard gets every {@code game.updates.*}
 * message and independently no-ops unless it actually hosts that room in
 * memory). For a room it does host, it fans the raw JSON out to each
 * participant's own {@code gateway.outbound.<sessionId>} inbox -- the
 * exact channel {@code ChessWebSocketHandler} already listens on to
 * deliver to whichever WS Gateway process is actually holding that
 * session's real socket, regardless of which shard produced the
 * message. This mirrors {@code RoomMessenger.sendTo}'s per-session
 * routing, just applied to every seated session instead of one.
 */
@Slf4j
@Component
public class GameUpdatesNatsSubscriber {

    private final RoomRegistry roomRegistry;

    public GameUpdatesNatsSubscriber(RoomRegistry roomRegistry) {
        this.roomRegistry = roomRegistry;

        NatsBridge.subscribe("game.updates.*", (subject, payload) -> {
            String roomId = extractRoomId(subject);
            handleUpdate(roomId, payload);
        });
    }

    private void handleUpdate(String roomId, String payload) {
        GameRoom room = roomRegistry.getRoom(roomId);
        if (room == null) {
            // Not hosted on this shard -- whichever shard does host it
            // will independently pick up the same broadcast.
            return;
        }

        for (WebSocketSession session : room.getSessions()) {
            try {
                NatsBridge.publish("gateway.outbound." + session.getId(), payload);
            } catch (Exception e) {
                log.error("[GAME UPDATES] Failed to forward update for room {} to session {}: {}",
                        roomId, session.getId(), e.getMessage(), e);
            }
        }
    }

    private String extractRoomId(String subject) {
        return subject.substring(subject.lastIndexOf('.') + 1);
    }
}