package org.example.network.server.connection;

import lombok.extern.slf4j.Slf4j;
import org.example.network.nats.NatsBridge;
import org.example.network.server.room.RoomRegistry;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AuthNatsSubscriber {

    private final AuthHandler authHandler;
    private final RoomRegistry roomRegistry;

    public AuthNatsSubscriber(AuthHandler authHandler, RoomRegistry roomRegistry) {
        this.authHandler = authHandler;
        this.roomRegistry = roomRegistry;

        // הרשמה לבקשות אימות שמגיעות מה-Gateway דרך NATS
        NatsBridge.subscribeQueue("auth.requests.*", "auth-service", (subject, payload) -> {            String sessionId = extractSessionId(subject);
            handleAuthRequest(sessionId, payload);
        });
    }

    private void handleAuthRequest(String sessionId, String payload) {
        try {
            // אדפטור שמקשר בין תגובת ה-AuthHandler ל-NATS
            NatsWebSocketSessionAdapter virtualSession = new NatsWebSocketSessionAdapter(sessionId);

            if (payload.contains("\"type\":\"LOGIN\"")) {
                authHandler.processLoginRequest(virtualSession, payload, roomRegistry);
            } else if (payload.contains("\"type\":\"RECONNECT\"")) {
                authHandler.processReconnectRequest(virtualSession, payload, roomRegistry);
            } else if (payload.contains("\"type\":\"JOIN_ROOM\"")) {
                authHandler.processJoinRoomRequest(virtualSession, payload, roomRegistry);
            } else if (payload.contains("\"type\":\"JOIN_MATCH\"")) {
                authHandler.processJoinMatchRequest(virtualSession, payload, roomRegistry);
            }
        } catch (Exception e) {
            log.error("[AUTH SERVICE] Error processing auth request via NATS: {}", e.getMessage(), e);
        }
    }

    private String extractSessionId(String subject) {
        return subject.substring(subject.lastIndexOf('.') + 1);
    }
}