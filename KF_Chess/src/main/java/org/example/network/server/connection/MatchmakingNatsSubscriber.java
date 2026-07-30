package org.example.network.server.connection;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.database.UserRepository;
import org.example.network.nats.NatsBridge;
import org.example.network.server.room.MatchmakingManager;
import org.example.network.server.room.PlayerInfo;
import org.example.network.server.room.RoomRegistry;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class MatchmakingNatsSubscriber {

    private final MatchmakingManager matchmakingManager;
    private final RoomRegistry roomRegistry;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public MatchmakingNatsSubscriber(MatchmakingManager matchmakingManager, RoomRegistry roomRegistry,
                                     UserRepository userRepository, ObjectMapper objectMapper) {
        this.matchmakingManager = matchmakingManager;
        this.roomRegistry = roomRegistry;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;

        // הרשמה לבקשות matchmaking שמגיעות מה-Gateway דרך NATS
        NatsBridge.subscribeQueue("matchmaking.requests.*", "matchmaking-service", (subject, payload) -> {            String sessionId = extractSessionId(subject);
            handleMatchmakingRequest(sessionId, payload);
        });
    }

    private void handleMatchmakingRequest(String sessionId, String payload) {
        try {
            Map<String, Object> root = objectMapper.readValue(payload, Map.class);
            String type = (String) root.get("type");
            if (type == null) return;

            NatsWebSocketSessionAdapter virtualSession = new NatsWebSocketSessionAdapter(sessionId);

            switch (type) {
                case "FIND_MATCH" -> handleFindMatch(sessionId, virtualSession);
                case "CANCEL_MATCHMAKING" -> {
                    matchmakingManager.removeFromQueue(virtualSession);
                    sendResponse(virtualSession, "{\"type\":\"MATCHMAKING_CANCELLED\"}");
                }
                default -> log.debug("[MATCHMAKING SERVICE] Ignored unrecognized type: {}", type);
            }
        } catch (Exception e) {
            log.error("[MATCHMAKING SERVICE] Error processing matchmaking request via NATS: {}", e.getMessage(), e);
        }
    }

    private void handleFindMatch(String sessionId, NatsWebSocketSessionAdapter virtualSession) {
        PlayerInfo player = roomRegistry.getPlayerBySessionId(sessionId);
        if (player == null) {
            sendResponse(virtualSession, "{\"type\":\"MATCHMAKING_REJECTED\",\"reason\":\"Must be logged in to find match\"}");
            return;
        }

        userRepository.getRatingAsync(player.username())
                .thenAccept(rating -> matchmakingManager.addToQueue(virtualSession, player.username(), rating))
                .exceptionally(ex -> {
                    log.error("[MATCHMAKING SERVICE] Failed to fetch rating for matchmaking (user: {}): {}",
                            player.username(), ex.getMessage());
                    sendResponse(virtualSession, "{\"type\":\"MATCHMAKING_REJECTED\",\"reason\":\"Database error\"}");
                    return null;
                });
    }

    private void sendResponse(NatsWebSocketSessionAdapter session, String text) {
        try {
            session.sendMessage(new org.springframework.web.socket.TextMessage(text));
        } catch (Exception e) {
            log.error("[MATCHMAKING SERVICE] Error sending response: {}", e.getMessage());
        }
    }

    private String extractSessionId(String subject) {
        return subject.substring(subject.lastIndexOf('.') + 1);
    }
}