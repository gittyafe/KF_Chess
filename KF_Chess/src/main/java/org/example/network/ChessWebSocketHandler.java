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
    // Tracks which room a *participant* (not spectator) is currently in, so
    // a dropped connection can find its way back via RECONNECT. Entries are
    // cleaned up by GameRoom's onEnded callback once a game truly finishes.
    private final Map<String, GameRoom> usernameToRoom = new ConcurrentHashMap<>();
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

        messageHandler.processMessage(session, payload, rooms, usernameToRoom, sessionToRoom, players);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        matchmakingManager.removeFromQueue(session);
        players.remove(session);
        GameRoom room = sessionToRoom.remove(session);

        if (room == null) return;

        room.getSessions().remove(session);

        if (room.isStarted() && !room.isEnded()) {
            // Deliberately do NOT remove the room here. GameRoom keeps its
            // loop/scheduler alive for a 20s grace period so the player can
            // reconnect (see RECONNECT handling in AuthHandler). Cleanup of
            // `rooms` / `usernameToRoom` happens automatically via the
            // onEnded callback once endGame() fires -- whether that's from
            // the disconnect timing out or the game finishing normally.
            System.out.println("Player disconnected! Starting resign countdown...");
            room.handlePlayerDisconnect(session);
        } else if (!room.isStarted() && room.getSessions().isEmpty()) {
            // Nobody ever showed up to play against them -- no game to
            // reconnect into, safe to tear down immediately.
            rooms.remove(room.getRoomId());
            usernameToRoom.entrySet().removeIf(e -> e.getValue() == room);
            room.shutdown();
        }
    }
}
