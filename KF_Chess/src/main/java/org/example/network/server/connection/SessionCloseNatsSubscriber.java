package org.example.network.server.connection;

import lombok.extern.slf4j.Slf4j;
import org.example.network.nats.NatsBridge;
import org.example.network.server.room.GameRoom;
import org.example.network.server.room.PlayerInfo;
import org.example.network.server.room.RoomRegistry;
import org.springframework.stereotype.Component;

/**
 * Bridges {@code ChessWebSocketHandler.afterConnectionClosed}'s
 * {@code gateway.session.closed} publish (payload = raw sessionId, no
 * wildcard) to the actual game-logic side effects: telling the
 * owning shard's {@link GameRoom} that a participant dropped (starts the
 * resign countdown), and clearing the session out of {@link RoomRegistry}
 * (local map + Redis mirror). Without this, closing a WS connection was a
 * complete no-op on the game-logic side -- the opponent would wait forever
 * and the Redis session mirror would just sit there until its TTL expired.
 *
 * <p>Every shard subscribes to the same fixed subject and independently
 * no-ops if it doesn't host the session's room -- same broadcast pattern as
 * {@code GameCommandNatsSubscriber} / {@code NatsGameListener}.
 */
@Slf4j
@Component
public class SessionCloseNatsSubscriber {

    private final RoomRegistry roomRegistry;

    public SessionCloseNatsSubscriber(RoomRegistry roomRegistry) {
        this.roomRegistry = roomRegistry;

        NatsBridge.subscribe("gateway.session.closed", (subject, sessionId) -> handleClosed(sessionId));
    }

    private void handleClosed(String sessionId) {
        try {
            PlayerInfo player = roomRegistry.getPlayerBySessionId(sessionId);
            GameRoom room = roomRegistry.getRoomForSessionId(sessionId);

            if (room != null && player != null) {
                room.handleUserDisconnected(player.username());
            }

            NatsWebSocketSessionAdapter virtualSession = new NatsWebSocketSessionAdapter(sessionId);
            roomRegistry.dropSession(virtualSession);

        } catch (Exception e) {
            log.error("[SESSION] Error handling gateway session close for {}: {}", sessionId, e.getMessage(), e);
        }
    }
}