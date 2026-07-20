package org.example.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.database.DatabaseManager;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

public class AuthHandler {

    private final ObjectMapper objectMapper;

    public AuthHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * פונקציה ראשית לטיפול בבקשת JOIN
     */
    public void processJoinRequest(
            WebSocketSession session,
            String payload,
            Map<String, GameRoom> rooms,
            Map<WebSocketSession, GameRoom> sessionToRoom,
            Map<WebSocketSession, PlayerInfo> players) {

        try {
            Map<String, Object> root = objectMapper.readValue(payload, Map.class);
            if (!"JOIN".equals(root.get("type"))) return;

            String username = (String) root.get("username");
            String password = (String) root.get("password");
            String roomId = (String) root.get("roomId");

            // 1. ולידציית קלט
            if (!validateInput(session, username, password, roomId)) {
                return;
            }

            // 2. אימות מול DB (SQLite)
            int rating = authenticateUser(session, username, password);
            if (rating == -1) {
                return; // שגיאה נשלחה בתוך authenticateUser
            }

            // 3. הצטרפות לחדר
            joinRoom(session, username, roomId, rating, rooms, sessionToRoom, players);

        } catch (Exception e) {
            System.err.println("❌ Error processing join request: " + e.getMessage());
        }
    }

    /**
     * פונקציית עזר: בדיקת תקינות השדות הנכנסים
     */
    private boolean validateInput(WebSocketSession session, String username, String password, String roomId) {
        if (username == null || username.isBlank() ||
                password == null || password.isBlank() ||
                roomId == null || roomId.isBlank()) {

            sendResponse(session, "{\"type\":\"JOIN_REJECTED\",\"reason\":\"Missing username, password or room ID\"}");
            return false;
        }
        return true;
    }

    /**
     * פונקציית עזר: אימות/הרשמה מול SQLite
     */
    private int authenticateUser(WebSocketSession session, String username, String password) {
        int rating = DatabaseManager.authenticateOrRegister(username, password);
        if (rating == -1) {
            sendResponse(session, "{\"type\":\"JOIN_REJECTED\",\"reason\":\"Invalid password or database error\"}");
        }
        return rating;
    }

    /**
     * פונקציית עזר: שיוך השחקן לחדר והפעלת הליכה/משחק
     */
    private void joinRoom(
            WebSocketSession session,
            String username,
            String roomId,
            int rating,
            Map<String, GameRoom> rooms,
            Map<WebSocketSession, GameRoom> sessionToRoom,
            Map<WebSocketSession, PlayerInfo> players) {

        GameRoom room = rooms.computeIfAbsent(roomId, id -> new GameRoom(id));

        synchronized (room) {
            char color = room.getSessions().isEmpty() ? 'W' : 'B';

            boolean success = room.addPlayer(session, username);
            if (!success) {
                sendResponse(session, "{\"type\":\"JOIN_REJECTED\",\"reason\":\"Room is full\"}");
                return;
            }

            // עדכון המפות הראשיות בשרת
            PlayerInfo playerInfo = new PlayerInfo(username, color);
            players.put(session, playerInfo);
            sessionToRoom.put(session, room);

            System.out.println("👤 User " + username + " (" + rating + " ELO) joined Room " + roomId + " as " + color);

            // אישור הצטרפות ללקוח
            sendResponse(session, String.format(
                    "{\"type\":\"JOIN_ACCEPTED\",\"username\":\"%s\",\"color\":\"%c\",\"rating\":%d}",
                    username, color, rating
            ));

            // אם החדר התמלא והמשחק התחיל
            if (room.isStarted()) {
                notifyGameStarted(room);
            }
        }
    }

    /**
     * פונקציית עזר: הודעה לכל היושבים בחדר שהמשחק התחיל
     */
    private void notifyGameStarted(GameRoom room) {
        String gameStartedPayload = String.format(
                "{\"type\":\"GAME_STARTED\",\"data\":[\"%s\",\"%s\"]}",
                room.getWhiteUsername(), room.getBlackUsername()
        );
        room.broadcast(gameStartedPayload);
        room.startLoop();
    }

    /**
     * פונקציית עזר לשליחה בטוחה ל-WebSocket
     */
    private void sendResponse(WebSocketSession session, String messageText) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(messageText));
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to send message to session: " + e.getMessage());
        }
    }
}