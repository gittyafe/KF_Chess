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


    public void processJoinRoomRequest(
            WebSocketSession session,
            String payload,
            Map<String, GameRoom> rooms,
            Map<WebSocketSession, GameRoom> sessionToRoom,
            Map<WebSocketSession, PlayerInfo> players) {
        try {
            JoinRequest joinReq = objectMapper.readValue(payload, JoinRequest.class);
            if (!"JOIN_ROOM".equals(joinReq.type())) return;
            if (!validateInput(session, joinReq)) return;

            int rating = authenticateUser(session, joinReq.username(), joinReq.password());
            if (rating == -1) return;

            GameRoom room = rooms.get(joinReq.roomId());
            if (room == null) {
                sendJsonResponse(session, new JoinRejectedResponse("Room does not exist"));
                return;
            }

            addUserToRoom(session, joinReq.username(), joinReq.roomId(), rating, room, sessionToRoom, players);

        } catch (Exception e) {
            System.err.println("❌ Error processing JOIN_ROOM request: " + e.getMessage());
            sendJsonResponse(session, new JoinRejectedResponse("Invalid request payload"));
        }
    }

    public void processJoinMatchRequest(
            WebSocketSession session,
            String payload,
            Map<String, GameRoom> rooms,
            Map<WebSocketSession, GameRoom> sessionToRoom,
            Map<WebSocketSession, PlayerInfo> players) {
        try {
            JoinRequest joinReq = objectMapper.readValue(payload, JoinRequest.class);

            if (!"JOIN_MATCH".equals(joinReq.type())) return;

            if (!validateInput(session, joinReq)) return;

            int rating = authenticateUser(session, joinReq.username(), joinReq.password());
            if (rating == -1) return;

            GameRoom room = rooms.computeIfAbsent(joinReq.roomId(), GameRoom::new);

            addUserToRoom(session, joinReq.username(), joinReq.roomId(), rating, room, sessionToRoom, players);

        } catch (Exception e) {
            System.err.println("❌ Error processing JOIN_MATCH request: " + e.getMessage());
            sendJsonResponse(session, new JoinRejectedResponse("Failed to join match"));
        }
    }

    private void addUserToRoom(
            WebSocketSession session,
            String username,
            String roomId,
            int rating,
            GameRoom room,
            Map<WebSocketSession, GameRoom> sessionToRoom,
            Map<WebSocketSession, PlayerInfo> players) {

        synchronized (room) {
            // הקצאת צבע: השחקן הראשון לבן ('W'), השני שחור ('B')
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

            // שליחת אישור הצטרפות חזרה ללקוח
            sendJsonResponse(session, new JoinAcceptedResponse(username, color, rating));

            // אם החדר התמלא והמשחק התחיל -> מודיעים לשני השחקנים
            if (room.isStarted()) {
                notifyGameStarted(room);
            }
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

// בתוך AuthHandler.java

    public void processLoginRequest(
            WebSocketSession session,
            String payload,
            Map<WebSocketSession, PlayerInfo> players) { // 👈 הוסיפי את players לפרמטרים!
        try {
            LoginRequest loginReq = objectMapper.readValue(payload, LoginRequest.class);

            if (isInvalid(loginReq.username()) || isInvalid(loginReq.password())) {
                sendJsonResponse(session, new LoginRejectedResponse("Missing username or password"));
                return;
            }

            int rating = DatabaseManager.authenticateOrRegister(loginReq.username(), loginReq.password());

            if (rating == -1) {
                sendJsonResponse(session, new LoginRejectedResponse("Invalid password or database error"));
                return;
            }

            // 🟢 1. שמירת המשתמש ישירות ב-players Map של השרת!
            // ה-'W' הוא צבע זמני בלבד, עד שמוצאים יריב ב-Matchmaking
            PlayerInfo playerInfo = new PlayerInfo(loginReq.username(), 'W');
            players.put(session, playerInfo);

            // 🟢 2. שמירה גיבוי ב-Session attributes
            session.getAttributes().put("username", loginReq.username());
            session.getAttributes().put("rating", rating);

            System.out.printf("🔑 User %s (%d ELO) logged in successfully%n", loginReq.username(), rating);

            sendJsonResponse(session, new LoginSuccessResponse(loginReq.username(), rating));

        } catch (Exception e) {
            System.err.println("❌ שגיאה בעיבוד בקשת התחברות: " + e.getMessage());
            sendJsonResponse(session, new LoginRejectedResponse("Invalid JSON format"));
        }
    }
}