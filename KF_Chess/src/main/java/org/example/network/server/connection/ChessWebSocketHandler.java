package org.example.network.server.connection;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.database.UserRepository;
import org.example.network.server.room.GameRoom;
import org.example.network.server.room.MatchmakingManager;
import org.example.network.server.room.RoomRegistry;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
public class ChessWebSocketHandler extends TextWebSocketHandler {

    private final RoomRegistry registry = new RoomRegistry();
    private final MatchmakingManager matchmakingManager;
    private final MessageHandler messageHandler;

    public ChessWebSocketHandler() {
        ObjectMapper objectMapper = new ObjectMapper();
        UserRepository userRepository = new UserRepository();
        AuthHandler authHandler = new AuthHandler(objectMapper, userRepository);
        this.matchmakingManager = new MatchmakingManager(objectMapper);
        this.messageHandler = new MessageHandler(objectMapper, authHandler, matchmakingManager, userRepository);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        if (payload == null || payload.isBlank()) return;

        messageHandler.processMessage(session, payload, registry);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        matchmakingManager.removeFromQueue(session);
        GameRoom room = registry.dropSession(session);

        if (room == null) return;

        room.removeSession(session);

        if (room.isStarted() && !room.isEnded()) {
            // Deliberately do NOT remove the room here. GameRoom keeps its
            // loop/scheduler alive for a grace period so the player can
            // reconnect (see RECONNECT/LOGIN handling in AuthHandler).
            // Cleanup of the registry happens automatically via GameRoom's
            // onEnded callback once endGame() fires.
            log.info("[SERVER OUT] Player disconnected! Starting resign countdown...");
            room.handlePlayerDisconnect(session);
        } else if (!room.isStarted() && room.getSessions().isEmpty()) {
            // Nobody ever showed up to play against them -- no game to
            // reconnect into, safe to tear down immediately.
            log.info("[SERVER OUT] Room {} is abandoned and will be removed.", room.getRoomId());
            registry.unregisterRoomIfAbandoned(room);
            room.shutdown();
        }
    }
}
