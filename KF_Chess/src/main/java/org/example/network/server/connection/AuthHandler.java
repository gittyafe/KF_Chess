package org.example.network.server.connection;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.database.UserRepository;
import org.example.network.server.room.GameRoom;
import org.example.network.server.room.GameRoom.JoinResult;
import org.example.network.protocol.NetworkDTOs.*;
import org.example.network.server.room.RoomRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Slf4j
@Component
public class AuthHandler {

    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    public AuthHandler(ObjectMapper objectMapper, UserRepository userRepository) {
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
    }

    public void processJoinRoomRequest(WebSocketSession session, String payload, RoomRegistry registry) {
        try {
            JoinRequest joinReq = objectMapper.readValue(payload, JoinRequest.class);
            if (!"JOIN_ROOM".equals(joinReq.type())) return;
            if (!validateInput(session, joinReq)) return;

            authenticateUserAsync(session, joinReq.username(), joinReq.password(), rating -> {
                GameRoom room = registry.getRoom(joinReq.roomId());
                if (room == null) {
                    sendJsonResponse(session, new JoinRejectedResponse("Room does not exist"));
                    return;
                }

                addUserToRoom(session, joinReq.username(), joinReq.roomId(), rating, room, registry);
            });

        } catch (Exception e) {
            log.error("[SERVER ERROR] Error processing JOIN_ROOM request: {}", e.getMessage());
            sendJsonResponse(session, new JoinRejectedResponse("Invalid request payload"));
        }
    }

    public void processJoinMatchRequest(WebSocketSession session, String payload, RoomRegistry registry) {
        try {
            JoinRequest joinReq = objectMapper.readValue(payload, JoinRequest.class);

            if (!"JOIN_MATCH".equals(joinReq.type())) return;
            if (!validateInput(session, joinReq)) return;

            authenticateUserAsync(session, joinReq.username(), joinReq.password(), rating -> {
                GameRoom room = registry.getOrCreateRoom(joinReq.roomId());
                addUserToRoom(session, joinReq.username(), joinReq.roomId(), rating, room, registry);
            });

        } catch (Exception e) {
            log.error("[SERVER ERROR] Error processing JOIN_MATCH request: {}", e.getMessage());
            sendJsonResponse(session, new JoinRejectedResponse("Failed to join match"));
        }
    }

    public void processReconnectRequest(WebSocketSession session, String payload, RoomRegistry registry) {
        try {
            LoginRequest req = objectMapper.readValue(payload, LoginRequest.class);

            if (isInvalid(req.username()) || isInvalid(req.password())) {
                sendJsonResponse(session, new ReconnectRejectedResponse("Missing username or password"));
                return;
            }

            userRepository.authenticateOrRegisterAsync(req.username(), req.password())
                    .thenAccept(rating -> {
                        if (rating == -1) {
                            sendJsonResponse(session, new ReconnectRejectedResponse("Invalid password or database error"));
                            return;
                        }

                        GameRoom room = tryReconnectIntoActiveGame(session, req.username(), registry);
                        if (room == null) {
                            sendJsonResponse(session, new ReconnectRejectedResponse("No active game to reconnect to"));
                            return;
                        }

                        sendJsonResponse(session, new ReconnectAcceptedResponse(req.username(), room.getColorForUsername(req.username()), rating));
                    })
                    .exceptionally(ex -> {
                        log.error("[SERVER ERROR] Error during async reconnect for user {}: {}", req.username(), ex.getMessage());
                        sendJsonResponse(session, new ReconnectRejectedResponse("Database error"));
                        return null;
                    });

        } catch (Exception e) {
            log.error("[SERVER ERROR] Error processing RECONNECT request: {}", e.getMessage());
            sendJsonResponse(session, new ReconnectRejectedResponse("Invalid request payload"));
        }
    }

    public void processLoginRequest(WebSocketSession session, String payload, RoomRegistry registry) {
        try {
            LoginRequest loginReq = objectMapper.readValue(payload, LoginRequest.class);

            if (isInvalid(loginReq.username()) || isInvalid(loginReq.password())) {
                sendJsonResponse(session, new LoginRejectedResponse("Missing username or password"));
                return;
            }

            userRepository.authenticateOrRegisterAsync(loginReq.username(), loginReq.password())
                    .thenAccept(rating -> {
                        if (rating == -1) {
                            sendJsonResponse(session, new LoginRejectedResponse("Invalid password or database error"));
                            return;
                        }

                        session.getAttributes().put("username", loginReq.username());
                        session.getAttributes().put("rating", rating);

                        log.info("[SERVER OUT] User {} ({}) logged in successfully", loginReq.username(), rating);
                        sendJsonResponse(session, new LoginSuccessResponse(loginReq.username(), rating));

                        GameRoom room = tryReconnectIntoActiveGame(session, loginReq.username(), registry);
                        if (room != null) {
                            log.info("[SERVER OUT] Login doubled as a reconnect into room {}", room.getRoomId());
                        } else {
                            registry.registerPlayerInfo(session, loginReq.username(), 'W');
                        }
                    })
                    .exceptionally(ex -> {
                        log.error("[SERVER ERROR] Error during async login for user {}: {}", loginReq.username(), ex.getMessage());
                        sendJsonResponse(session, new LoginRejectedResponse("Database error"));
                        return null;
                    });

        } catch (Exception e) {
            log.error("[SERVER ERROR] Error processing login request: {}", e.getMessage());
            sendJsonResponse(session, new LoginRejectedResponse("Invalid JSON format"));
        }
    }

    private GameRoom tryReconnectIntoActiveGame(WebSocketSession session, String username, RoomRegistry registry) {
        GameRoom room = registry.getActiveRoomForUsername(username);
        if (room == null) return null;

        boolean rebound = room.reconnectPlayer(session, username);
        if (!rebound) return null;

        char color = room.getColorForUsername(username);
        registry.bindParticipant(session, username, room, color);

        log.info("User {} reconnected to room {}", username, room.getRoomId());
        return room;
    }

    private void addUserToRoom(WebSocketSession session, String username, String roomId, int rating,
                               GameRoom room, RoomRegistry registry) {

        JoinResult result = room.addPlayer(session, username);

        if (result.role() != GameRoom.JoinRole.SPECTATOR) {
            registry.bindParticipant(session, username, room, result.color());
        } else {
            registry.bindSpectator(session, username, room);
        }

        log.info("[SERVER OUT] User {} ({}) joined Room {} as {}", username, rating, roomId, result.role());
        sendJsonResponse(session, new JoinAcceptedResponse(username, result.color(), rating));
    }

    private boolean validateInput(WebSocketSession session, JoinRequest req) {
        if (isInvalid(req.username()) || isInvalid(req.password()) || isInvalid(req.roomId())) {
            sendJsonResponse(session, new JoinRejectedResponse("Missing username, password or room ID"));
            return false;
        }
        return true;
    }

    private boolean isInvalid(String str) {
        return str == null || str.isBlank();
    }

    /**
     * Helper method to handle asynchronous authentication with a callback upon success.
     */
    private void authenticateUserAsync(WebSocketSession session, String username, String password, Consumer<Integer> onSuccess) {
        userRepository.authenticateOrRegisterAsync(username, password)
                .thenAccept(rating -> {
                    if (rating == -1) {
                        sendJsonResponse(session, new JoinRejectedResponse("Invalid password or database error"));
                    } else {
                        onSuccess.accept(rating);
                    }
                })
                .exceptionally(ex -> {
                    log.error("[SERVER ERROR] Auth error for user {}: {}", username, ex.getMessage());
                    sendJsonResponse(session, new JoinRejectedResponse("Database error"));
                    return null;
                });
    }

    private void sendJsonResponse(WebSocketSession session, Object responseObj) {
        try {
            if (session.isOpen()) {
                String json = objectMapper.writeValueAsString(responseObj);
                session.sendMessage(new TextMessage(json));
            }
        } catch (Exception e) {
            log.error("[SERVER ERROR] Error sending message to client: {}", e.getMessage());
        }
    }
}