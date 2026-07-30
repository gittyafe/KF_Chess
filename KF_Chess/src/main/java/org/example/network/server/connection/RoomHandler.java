package org.example.network.server.connection;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.network.server.room.GameRoom;
import org.example.network.server.room.RoomRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

@Slf4j
@Component
public class RoomHandler {

    private final ObjectMapper objectMapper;

    public RoomHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void processCreateRoomRequest(WebSocketSession session, String payload, RoomRegistry registry) {
        try {
            Map<String, Object> root = objectMapper.readValue(payload, Map.class);
            String roomId = root.get("roomId") != null ? root.get("roomId").toString().trim() : "";
            String username = root.get("username") != null ? root.get("username").toString().trim() : "";

            if (roomId.isEmpty()) {
                sendResponse(session, "{\"type\":\"CREATE_REJECTED\",\"message\":\"Room ID cannot be empty\"}");
                return;
            }
            if (username.isEmpty()) {
                sendResponse(session, "{\"type\":\"CREATE_REJECTED\",\"message\":\"Username cannot be empty\"}");
                return;
            }

            GameRoom newRoom = registry.tryCreateRoom(roomId);
            if (newRoom == null) {
                log.info("[SERVER OUT] Room creation failed: {} already exists!", roomId);
                sendResponse(session, "{\"type\":\"CREATE_REJECTED\",\"message\":\"Room ID '" + roomId
                        + "' is already taken. Choose another name.\"}");
                return;
            }

            // GameRoom.addPlayer is the single source of truth for color
            // assignment; the creator is always seated as the first (white) player.
            GameRoom.JoinResult result = newRoom.addPlayer(session, username);
            registry.bindParticipant(session, username, newRoom, result.color());

            log.info("[SERVER OUT] Room created successfully: [{}] by user: {}", roomId, username);
            sendResponse(session, "{\"type\":\"CREATE_ACCEPTED\",\"roomId\":\"" + roomId
                    + "\",\"message\":\"Room created successfully. Waiting for opponent.\"}");

        } catch (Exception e) {
            log.error("[SERVER ERROR] Error processing CREATE_ROOM request: {}", e.getMessage());
            sendResponse(session, "{\"type\":\"CREATE_REJECTED\",\"message\":\"Invalid request payload\"}");
        }
    }

    private void sendResponse(WebSocketSession session, String text) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(text));
            }
        } catch (Exception e) {
            log.error("[SERVER ERROR] Error sending WebSocket response: {}", e.getMessage());
        }
    }
}