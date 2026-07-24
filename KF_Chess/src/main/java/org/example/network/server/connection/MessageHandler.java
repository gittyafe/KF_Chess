package org.example.network.server.connection;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.database.UserRepository;
import org.example.network.server.game.GameCommandHandler;
import org.example.network.server.room.GameRoom;
import org.example.network.server.room.MatchmakingManager;
import org.example.network.server.room.PlayerInfo;
import org.example.network.server.room.RoomRegistry;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

/**
 * Top-level dispatcher for incoming WebSocket text frames: routes JSON
 * control messages (login, room join, matchmaking) to AuthHandler /
 * MatchmakingManager, and raw move/jump protocol strings to
 * GameCommandHandler. Doesn't itself know how to authenticate, seat a
 * player, or validate a chess move -- it only decides who does.
 */
@Slf4j
public class MessageHandler {

    private final ObjectMapper objectMapper;
    private final AuthHandler authHandler;
    private final MatchmakingManager matchmakingManager;
    private final UserRepository userRepository;
    private final GameCommandHandler gameCommandHandler = new GameCommandHandler();

    public MessageHandler(ObjectMapper objectMapper, AuthHandler authHandler,
                           MatchmakingManager matchmakingManager, UserRepository userRepository) {
        this.objectMapper = objectMapper;
        this.authHandler = authHandler;
        this.matchmakingManager = matchmakingManager;
        this.userRepository = userRepository;
    }

    public void processMessage(WebSocketSession session, String payload, RoomRegistry registry) {
        if (isJsonPayload(payload)) {
            handleJsonMessage(session, payload, registry);
        } else {
            handleGameCommand(session, payload, registry);
        }
    }

    private boolean isJsonPayload(String payload) {
        return payload.trim().startsWith("{");
    }

    private void handleJsonMessage(WebSocketSession session, String payload, RoomRegistry registry) {
        try {
            Map<String, Object> root = objectMapper.readValue(payload, Map.class);
            String type = (String) root.get("type");
            if (type == null) return;

            switch (type) {
                case "LOGIN" -> authHandler.processLoginRequest(session, payload, registry);
                case "RECONNECT" -> authHandler.processReconnectRequest(session, payload, registry);
                case "CREATE_ROOM" -> processCreateRoomRequest(session, root, registry);
                case "JOIN_ROOM" -> authHandler.processJoinRoomRequest(session, payload, registry);
                case "JOIN_MATCH" -> authHandler.processJoinMatchRequest(session, payload, registry);
                case "FIND_MATCH" -> handleFindMatchRequest(session, registry);
                case "CANCEL_MATCHMAKING" -> {
                    matchmakingManager.removeFromQueue(session);
                    sendResponse(session, "{\"type\":\"MATCHMAKING_CANCELLED\"}");
                }
                default -> System.err.println("Unknown JSON message type: " + type);
            }
        } catch (Exception e) {
            log.error("[SERVER ERROR] Error parsing JSON message: {}", e.getMessage());
        }
    }

    private void processCreateRoomRequest(WebSocketSession session, Map<String, Object> root, RoomRegistry registry) {
        String roomId = root.get("roomId") != null ? root.get("roomId").toString().trim() : "";
        String username = root.get("username") != null ? root.get("username").toString().trim() : "";

        if (roomId.isEmpty()) {
            sendResponse(session, "{\"type\":\"CREATE_REJECTED\",\"message\":\"Room ID cannot be empty\"}");
            return;
        }

        GameRoom newRoom = registry.tryCreateRoom(roomId);
        if (newRoom == null) {
            log.info("[SERVER OUT] Room creation failed: " + roomId + " already exists!");
            sendResponse(session, "{\"type\":\"CREATE_REJECTED\",\"message\":\"Room ID '" + roomId + "' is already taken. Choose another name.\"}");
            return;
        }

        // GameRoom.addPlayer is the single source of truth for color
        // assignment; the creator is always seated as the first (white) player.
        GameRoom.JoinResult result = newRoom.addPlayer(session, username);
        registry.bindParticipant(session, username, newRoom, result.color());

        log.info("[SERVER OUT] Room created successfully: [" + roomId + "] by user: " + username);

        sendResponse(session, "{\"type\":\"CREATE_ACCEPTED\",\"roomId\":\"" + roomId + "\",\"message\":\"Room created successfully. Waiting for opponent.\"}");
    }

    private void handleFindMatchRequest(WebSocketSession session, RoomRegistry registry) {
        PlayerInfo player = registry.getPlayer(session);
        if (player != null) {
            int rating = userRepository.getRating(player.username());
            matchmakingManager.addToQueue(session, player.username(), rating);
        } else {
            sendResponse(session, "{\"type\":\"MATCHMAKING_REJECTED\",\"reason\":\"Must be logged in to find match\"}");
        }
    }

    private void handleGameCommand(WebSocketSession session, String payload, RoomRegistry registry) {
        PlayerInfo player = registry.getPlayer(session);
        GameRoom room = registry.getRoomForSession(session);

        if (player == null || room == null || !room.isStarted()) return;

        gameCommandHandler.handle(room, player, payload);
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
