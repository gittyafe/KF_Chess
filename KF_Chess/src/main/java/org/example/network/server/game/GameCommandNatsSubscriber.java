package org.example.network.server.game;

import lombok.extern.slf4j.Slf4j;
import org.example.network.nats.NatsBridge;
import org.example.network.server.room.GameRoom;
import org.example.network.server.room.PlayerInfo;
import org.example.network.server.room.RoomRegistry;
import org.springframework.stereotype.Component;

/**
 * Closes a gap that meant no move ever reached a {@link GameRoom} once the
 * WS Gateway and the Game Shard became separate processes:
 *
 * <p>{@code ChessWebSocketHandler.handleTextMessage} publishes every raw
 * in-game command (moves, jumps, ...) to
 * {@code "game.commands.<sessionId>"}, because the Gateway is
 * intentionally stateless and does not know which room a session belongs
 * to -- it only knows the session id. Nothing was subscribed to that
 * subject anywhere in the codebase. ({@code NatsGameListener} is
 * subscribed to the *similarly named but different* {@code
 * "game.events.*"}, keyed by roomId, which is a different flow used for
 * server-internal events.)
 *
 * <p>Every Game Shard should run one instance of this. On each command it
 * resolves {@code sessionId -> (room, player)} via {@link RoomRegistry}
 * (which transparently falls back to its Redis mirror for sessions this
 * shard never saw bound to a room locally -- see the class-level docs on
 * {@code RoomRegistry}), and only acts if the room it finds is one *this*
 * shard actually hosts in memory. Every shard subscribes to the same
 * wildcard and independently no-ops for rooms it doesn't own -- the same
 * "wildcard subscribe, ignore what's not mine" pattern
 * {@code NatsGameListener} already uses.
 *
 * <p>Wiring note: needs to be instantiated once at startup (constructor
 * injection of {@link RoomRegistry} makes it a normal Spring
 * {@code @Component} like {@code AuthNatsSubscriber} -- if
 * {@code RoomRegistry} isn't already a Spring bean elsewhere, add
 * {@code @Component} to it too, or provide a {@code @Bean} method).
 */
@Slf4j
@Component
public class GameCommandNatsSubscriber {

    private final RoomRegistry roomRegistry;
    private final GameCommandHandler gameCommandHandler;

    public GameCommandNatsSubscriber(RoomRegistry roomRegistry) {
        this.roomRegistry = roomRegistry;
        this.gameCommandHandler = new GameCommandHandler();

        NatsBridge.subscribe("game.commands.*", (subject, payload) -> {
            String sessionId = extractSessionId(subject);
            handleCommand(sessionId, payload);
        });
    }

    private void handleCommand(String sessionId, String payload) {
        GameRoom room = roomRegistry.getRoomForSessionId(sessionId);
        if (room == null) {
            // Either another shard hosts this session's room (normal --
            // it will pick up the same broadcast independently), or the
            // session was never bound to a room. Nothing to do here.
            return;
        }

        PlayerInfo player = roomRegistry.getPlayerBySessionId(sessionId);
        if (player == null || !room.isStarted()) {
            return;
        }

        try {
            gameCommandHandler.handle(room, player, payload);
        } catch (Exception e) {
            log.error("[GAME COMMAND] Failed to handle command for session {} in room {}: {}",
                    sessionId, room.getRoomId(), e.getMessage(), e);
        }
    }

    private String extractSessionId(String subject) {
        return subject.substring(subject.lastIndexOf('.') + 1);
    }
}
