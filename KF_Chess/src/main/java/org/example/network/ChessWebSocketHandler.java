package org.example.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChessWebSocketHandler extends TextWebSocketHandler {

    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, GameRoom> sessionToRoom = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, PlayerInfo> players = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AuthHandler authHandler = new AuthHandler(objectMapper);

    private final MatchmakingManager matchmakingManager = new MatchmakingManager(objectMapper);
    private final MessageHandler messageHandler = new MessageHandler(objectMapper, authHandler, matchmakingManager);

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        if (payload == null || payload.isBlank()) return;

        messageHandler.processMessage(session, payload, rooms, sessionToRoom, players);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        matchmakingManager.removeFromQueue(session);
        PlayerInfo player = players.remove(session);
        GameRoom room = sessionToRoom.remove(session);

        if (room != null) {
            synchronized (room) {
                room.getSessions().remove(session);
                if (room.getSessions().isEmpty()) {
                    room.stopLoop();
                    rooms.remove(room.getWhiteUsername()); // הסרה בטוחה
                }
            }
        }
        if (player != null) {
            System.out.println("🔌 Player disconnected: " + player.username());
        }
    }
}