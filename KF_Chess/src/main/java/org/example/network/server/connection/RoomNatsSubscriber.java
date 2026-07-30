package org.example.network.server.connection;

import lombok.extern.slf4j.Slf4j;
import org.example.network.nats.NatsBridge;
import org.example.network.server.room.RoomRegistry;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RoomNatsSubscriber {

    private final RoomHandler roomHandler;
    private final RoomRegistry roomRegistry;

    public RoomNatsSubscriber(RoomHandler roomHandler, RoomRegistry roomRegistry) {
        this.roomHandler = roomHandler;
        this.roomRegistry = roomRegistry;

        // הרשמה לבקשות יצירת חדר שמגיעות מה-Gateway דרך NATS
        NatsBridge.subscribeQueue("room.requests.*", "room-service", (subject, payload) -> {            String sessionId = extractSessionId(subject);
            handleRoomRequest(sessionId, payload);
        });
    }

    private void handleRoomRequest(String sessionId, String payload) {
        try {
            NatsWebSocketSessionAdapter virtualSession = new NatsWebSocketSessionAdapter(sessionId);

            if (payload.contains("\"type\":\"CREATE_ROOM\"")) {
                roomHandler.processCreateRoomRequest(virtualSession, payload, roomRegistry);
            } else {
                log.debug("[ROOM SERVICE] Ignored unrecognized room request payload: {}", payload);
            }
        } catch (Exception e) {
            log.error("[ROOM SERVICE] Error processing room request via NATS: {}", e.getMessage(), e);
        }
    }

    private String extractSessionId(String subject) {
        return subject.substring(subject.lastIndexOf('.') + 1);
    }
}