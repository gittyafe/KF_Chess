package org.example.network.server.connection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.network.nats.NatsBridge;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ChessWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, WebSocketSession> localSessions = new ConcurrentHashMap<>();

    public ChessWebSocketHandler() {
        // מקשיב לתשובות שחוזרות מכל ה-Services ב-NATS ומיועדות לשרת ה-WS הזה
        NatsBridge.subscribe("gateway.outbound.*", (subject, rawJson) -> {
            String targetSessionId = extractSessionIdFromSubject(subject);
            sendToLocalSession(targetSessionId, rawJson);
        });
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        localSessions.put(session.getId(), session);
        log.info("[WS GATEWAY] Client connected: {}", session.getId());
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();

        try {
            if (payload.trim().startsWith("{")) {
                JsonNode root = objectMapper.readTree(payload);
                String type = root.has("type") ? root.get("type").asText() : "";

                // מנתב את ההודעות לערוצי ה-NATS המתאימים לפי סוג ההודעה
                switch (type) {
                    case "LOGIN", "RECONNECT", "JOIN_ROOM", "JOIN_MATCH" ->
                            NatsBridge.publish("auth.requests." + session.getId(), payload);
                    case "CREATE_ROOM" ->
                            NatsBridge.publish("room.requests." + session.getId(), payload);
                    case "FIND_MATCH", "CANCEL_MATCHMAKING" ->
                            NatsBridge.publish("matchmaking.requests." + session.getId(), payload);
                    default ->
                            NatsBridge.publish("game.commands." + session.getId(), payload);
                }
            } else {
                NatsBridge.publish("game.commands." + session.getId(), payload);
            }
        } catch (Exception e) {
            log.error("[WS GATEWAY] Error routing message: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        localSessions.remove(session.getId());
        NatsBridge.publish("gateway.session.closed", session.getId());
        log.info("[WS GATEWAY] Client disconnected: {}", session.getId());
    }

    private void sendToLocalSession(String sessionId, String rawJson) {
        WebSocketSession session = localSessions.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(rawJson));
            } catch (Exception e) {
                log.error("[WS GATEWAY] Failed to send to session {}: {}", sessionId, e.getMessage());
            }
        }
    }

    private String extractSessionIdFromSubject(String subject) {
        return subject.substring(subject.lastIndexOf('.') + 1);
    }
}