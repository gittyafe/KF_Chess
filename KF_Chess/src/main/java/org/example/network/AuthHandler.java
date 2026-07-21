package org.example.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.database.DatabaseManager;
import org.example.network.NetworkDTOs.*;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;

public class AuthHandler {

    private final ObjectMapper objectMapper;

    public AuthHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * פונקציה ראשיות לטיפול בבקשות הצטרפות (JOIN).
     */
    public void processJoinRequest(
            WebSocketSession session,
            String payload,
            Map<String, GameRoom> rooms,
            Map<WebSocketSession, GameRoom> sessionToRoom,
            Map<WebSocketSession, PlayerInfo> players) {

        try {
            JoinRequest joinReq = objectMapper.readValue(payload, JoinRequest.class);
            if (!"JOIN".equals(joinReq.type())) return;

            if (!validateInput(session, joinReq)) {
                return;
            }

            int rating = authenticateUser(session, joinReq.username(), joinReq.password());
            if (rating == -1) {
                return;
            }

            joinRoom(session, joinReq.username(), joinReq.roomId(), rating, rooms, sessionToRoom, players);

        } catch (Exception e) {
            System.err.println("❌ שגיאה בעיבוד בקשת הצטרפות: " + e.getMessage());
        }
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
        int rating = DatabaseManager.authenticateOrRegister(username, password);
        if (rating == -1) {
            sendJsonResponse(session, new JoinRejectedResponse("Invalid password or database error"));
        }
        return rating;
    }

    private void joinRoom(
            WebSocketSession session,
            String username,
            String roomId,
            int rating,
            Map<String, GameRoom> rooms,
            Map<WebSocketSession, GameRoom> sessionToRoom,
            Map<WebSocketSession, PlayerInfo> players) {

        GameRoom room = rooms.computeIfAbsent(roomId, GameRoom::new);

        synchronized (room) {
            char color = room.getSessions().isEmpty() ? 'W' : 'B';

            boolean success = room.addPlayer(session, username);
            if (!success) {
                sendJsonResponse(session, new JoinRejectedResponse("Room is full"));
                return;
            }

            PlayerInfo playerInfo = new PlayerInfo(username, color);
            players.put(session, playerInfo);
            sessionToRoom.put(session, room);

            System.out.printf("👤 User %s (%d ELO) joined Room %s as %c%n", username, rating, roomId, color);

            sendJsonResponse(session, new JoinAcceptedResponse(username, color, rating));

            if (room.isStarted()) {
                notifyGameStarted(room);
            }
        }
    }

    private void notifyGameStarted(GameRoom room) {
        try {
            GameStartedResponse payload = new GameStartedResponse(room.getWhiteUsername(), room.getBlackUsername());
            room.broadcast(objectMapper.writeValueAsString(payload));
            room.startLoop();
        } catch (IOException e) {
            System.err.println("❌ שגיאה בשידור תחילת משחק: " + e.getMessage());
        }
    }

    private void sendJsonResponse(WebSocketSession session, Object responseObj) {
        try {
            if (session.isOpen()) {
                String json = objectMapper.writeValueAsString(responseObj);
                session.sendMessage(new TextMessage(json));
            }
        } catch (Exception e) {
            System.err.println("❌ שגיאה בשליחת הודעה לקוח: " + e.getMessage());
        }
    }
}