package org.example.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.database.UserRepository;
import org.example.network.GameRoom.JoinResult;
import org.example.network.NetworkDTOs.*;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

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

            int rating = authenticateUser(session, joinReq.username(), joinReq.password());
            if (rating == -1) return;

            GameRoom room = registry.getRoom(joinReq.roomId());
            if (room == null) {
                sendJsonResponse(session, new JoinRejectedResponse("Room does not exist"));
                return;
            }

            addUserToRoom(session, joinReq.username(), joinReq.roomId(), rating, room, registry);

        } catch (Exception e) {
            System.err.println("Error processing JOIN_ROOM request: " + e.getMessage());
            sendJsonResponse(session, new JoinRejectedResponse("Invalid request payload"));
        }
    }

    public void processJoinMatchRequest(WebSocketSession session, String payload, RoomRegistry registry) {
        try {
            JoinRequest joinReq = objectMapper.readValue(payload, JoinRequest.class);

            if (!"JOIN_MATCH".equals(joinReq.type())) return;
            if (!validateInput(session, joinReq)) return;

            int rating = authenticateUser(session, joinReq.username(), joinReq.password());
            if (rating == -1) return;

            GameRoom room = registry.getOrCreateRoom(joinReq.roomId());
            addUserToRoom(session, joinReq.username(), joinReq.roomId(), rating, room, registry);

        } catch (Exception e) {
            System.err.println("Error processing JOIN_MATCH request: " + e.getMessage());
            sendJsonResponse(session, new JoinRejectedResponse("Failed to join match"));
        }
    }

    /**
     * Shared by both explicit RECONNECT messages and plain LOGIN: if this
     * username is a participant in a room that hasn't ended, rebind their
     * new session into it. Returns the room on success, or null if there's
     * nothing to reconnect to.
     */
    private GameRoom tryReconnectIntoActiveGame(WebSocketSession session, String username, RoomRegistry registry) {
        GameRoom room = registry.getActiveRoomForUsername(username);
        if (room == null) return null;

        boolean rebound = room.reconnectPlayer(session, username);
        if (!rebound) return null;

        char color = room.getColorForUsername(username);
        registry.bindParticipant(session, username, room, color);

        System.out.printf("User %s reconnected to room %s%n", username, room.getRoomId());
        return room;
    }

    /**
     * Explicit RECONNECT message, used by ChessWebSocketClient's automatic
     * background retry after a socket drop within the same running client.
     */
    public void processReconnectRequest(WebSocketSession session, String payload, RoomRegistry registry) {
        try {
            LoginRequest req = objectMapper.readValue(payload, LoginRequest.class);

            if (isInvalid(req.username()) || isInvalid(req.password())) {
                sendJsonResponse(session, new ReconnectRejectedResponse("Missing username or password"));
                return;
            }

            int rating = userRepository.authenticateOrRegister(req.username(), req.password());
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

        } catch (Exception e) {
            System.err.println("Error processing RECONNECT request: " + e.getMessage());
            sendJsonResponse(session, new ReconnectRejectedResponse("Invalid request payload"));
        }
    }

    private void addUserToRoom(WebSocketSession session, String username, String roomId, int rating,
                                GameRoom room, RoomRegistry registry) {

        JoinResult result = room.addPlayer(session, username);

        if (result.role() != GameRoom.JoinRole.SPECTATOR) {
            registry.bindParticipant(session, username, room, result.color());
        } else {
            registry.bindSpectator(session, username, room);
        }

        System.out.printf("User %s (%d ELO) joined Room %s as %s%n", username, rating, roomId, result.role());

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

    private int authenticateUser(WebSocketSession session, String username, String password) {
        int rating = userRepository.authenticateOrRegister(username, password);
        if (rating == -1) {
            sendJsonResponse(session, new JoinRejectedResponse("Invalid password or database error"));
        }
        return rating;
    }

    private void sendJsonResponse(WebSocketSession session, Object responseObj) {
        try {
            if (session.isOpen()) {
                String json = objectMapper.writeValueAsString(responseObj);
                session.sendMessage(new TextMessage(json));
            }
        } catch (Exception e) {
            System.err.println("Error sending message to client: " + e.getMessage());
        }
    }

    /**
     * LOGIN now doubles as a reconnect path. This is the important part of
     * the fix: a player who reconnects by restarting their client (a new
     * process, a fresh ChessWebSocketClient/WebSocketSession) goes through
     * LOGIN, never RECONNECT -- so LOGIN has to check for an active game
     * itself, or a client-restart reconnect can never work.
     */
    public void processLoginRequest(WebSocketSession session, String payload, RoomRegistry registry) {
        try {
            LoginRequest loginReq = objectMapper.readValue(payload, LoginRequest.class);

            if (isInvalid(loginReq.username()) || isInvalid(loginReq.password())) {
                sendJsonResponse(session, new LoginRejectedResponse("Missing username or password"));
                return;
            }

            int rating = userRepository.authenticateOrRegister(loginReq.username(), loginReq.password());

            if (rating == -1) {
                sendJsonResponse(session, new LoginRejectedResponse("Invalid password or database error"));
                return;
            }

            session.getAttributes().put("username", loginReq.username());
            session.getAttributes().put("rating", rating);

            System.out.printf("User %s (%d ELO) logged in successfully%n", loginReq.username(), rating);
            sendJsonResponse(session, new LoginSuccessResponse(loginReq.username(), rating));

            // If they're a participant in a game that's still running,
            // silently pull them back in. GameRoom.reconnectPlayer sends
            // GAME_STARTED + BOARD_UPDATE to this session, which the existing
            // client GUI already knows how to render as "the game resumed" --
            // no client-side UI changes needed.
            GameRoom room = tryReconnectIntoActiveGame(session, loginReq.username(), registry);
            if (room != null) {
                System.out.println("Login doubled as a reconnect into room " + room.getRoomId());
            } else {
                // Not in an active game -- temporary color until they join one.
                registry.registerPlayerInfo(session, loginReq.username(), 'W');
            }

        } catch (Exception e) {
            System.err.println("Error processing login request: " + e.getMessage());
            sendJsonResponse(session, new LoginRejectedResponse("Invalid JSON format"));
        }
    }
}
