package org.example.network;

import org.example.models.Position;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChessWebSocketHandler extends TextWebSocketHandler {

    // מפה שמחזיקה את כל החדרים הפעילים לפי מפתח ה-Room ID
    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();

    // מפות קישור מהירות כדי לדעת מאיזה סשן הגענו לאיזה חדר/שחקן
    private final Map<WebSocketSession, GameRoom> sessionToRoom = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, PlayerInfo> players = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();

        if (payload == null || payload.isEmpty()) return;

        // טיפול בבקשות JSON (כמו התחברות לחדר)
        if (payload.charAt(0) == '{') {
            handleJoin(session, payload);
            return;
        }

        // וידוא שהשחקן רשום בחדר כלשהו
        PlayerInfo player = players.get(session);
        GameRoom room = sessionToRoom.get(session);
        if (player == null || room == null || !room.isStarted()) {
            return;
        }

        if (Character.toUpperCase(payload.charAt(0)) == 'J') {
            handleJumpCommand(room, player, payload);
        } else {
            handleMoveCommand(room, player, payload);
        }
    }

    private void handleJoin(WebSocketSession session, String payload) {
        try {
            Map<String, Object> root = objectMapper.readValue(payload, Map.class);
            if (!"JOIN".equals(root.get("type"))) return;

            String username = (String) root.get("username");
            String roomId = (String) root.get("roomId"); // נשלח מהלקוח

            if (username == null || username.isBlank() || roomId == null || roomId.isBlank()) {
                sendSafe(session, "{\"type\":\"JOIN_REJECTED\",\"reason\":\"Missing data\"}");
                return;
            }

            // יצירת חדר חדש אם אינו קיים, או שליפה של קיים
            GameRoom room = rooms.computeIfAbsent(roomId, id -> new GameRoom(id));

            synchronized (room) {
                char color = room.getSessions().isEmpty() ? 'W' : 'B'; // ראשון לבן, שני שחור

                boolean success = room.addPlayer(session, username);
                if (!success) {
                    sendSafe(session, "{\"type\":\"JOIN_REJECTED\",\"reason\":\"Room is full\"}");
                    return;
                }

                PlayerInfo playerInfo = new PlayerInfo(username, color);
                players.put(session, playerInfo);
                sessionToRoom.put(session, room);

                System.out.println("User " + username + " joined Room " + roomId + " as " + color);
                sendSafe(session, "{\"type\":\"JOIN_ACCEPTED\",\"username\":\"" + username + "\",\"color\":\"" + color + "\"}");

                // אם החדר התמלא והמשחק יכול להתחיל
                if (room.isStarted()) {
                    String gameStartedPayload = String.format(
                            "{\"type\":\"GAME_STARTED\",\"data\":[\"%s\",\"%s\"]}",
                            room.getWhiteUsername(), room.getBlackUsername()
                    );
                    room.broadcast(gameStartedPayload);

                    // הפעלת לולאת ה-Tick הייחודית לחדר זה
                    room.startLoop();
                }
            }
        } catch (Exception e) {
            System.err.println("Error processing join: " + e.getMessage());
        }
    }

    private void handleMoveCommand(GameRoom room, PlayerInfo player, String command) {
        if (!isWellFormedMoveCommand(command)) return;

        try {
            Position from = parseNotation(command.substring(2, 4));
            Position to = parseNotation(command.substring(4, 6));

            org.example.models.Piece piece = room.getGameEngine().getPieceAt(from);
            char expectedColor = player.getColor() == 'W' ? 'w' : 'b';

            if (piece == null || piece.getColor() != expectedColor) return;

            room.getGameEngine().requestMove(from, to);
        } catch (Exception e) {
            System.err.println("Error executing move: " + e.getMessage());
        }
    }

    private void handleJumpCommand(GameRoom room, PlayerInfo player, String command) {
        if (!isWellFormedJumpCommand(command)) return;

        try {
            Position destination = parseNotation(command.substring(2, 4));

            org.example.models.Piece piece = room.getGameEngine().getPieceAt(destination);
            char expectedColor = player.getColor() == 'W' ? 'w' : 'b';

            if (piece == null || piece.getColor() != expectedColor) return;

            room.getGameEngine().jumpRequest(destination);
        } catch (Exception e) {
            System.err.println("Error executing jump: " + e.getMessage());
        }
    }

    private boolean isWellFormedMoveCommand(String command) {
        if (command.length() != 6) return false;
        char colorChar = Character.toUpperCase(command.charAt(0));
        return (colorChar == 'W' || colorChar == 'B') && isValidSquare(command.substring(2, 4)) && isValidSquare(command.substring(4, 6));
    }

    private boolean isWellFormedJumpCommand(String command) {
        if (command.length() != 4) return false;
        char colorChar = Character.toUpperCase(command.charAt(1));
        return (colorChar == 'W' || colorChar == 'B') && isValidSquare(command.substring(2, 4));
    }

    private boolean isValidSquare(String square) {
        if (square.length() != 2) return false;
        char file = Character.toLowerCase(square.charAt(0));
        char rank = square.charAt(1);
        return file >= 'a' && file <= 'h' && rank >= '1' && rank <= '8';
    }

    private Position parseNotation(String notation) {
        int col = notation.charAt(0) - 'a';
        int row = 8 - Character.getNumericValue(notation.charAt(1));
        return new Position(row, col);
    }

    private void sendSafe(WebSocketSession session, String messageText) {
        try {
            if (session.isOpen()) session.sendMessage(new TextMessage(messageText));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) throws Exception {
        PlayerInfo player = players.remove(session);
        GameRoom room = sessionToRoom.remove(session);

        if (room != null) {
            synchronized (room) {
                room.getSessions().remove(session);
                if (room.getSessions().isEmpty()) {
                    rooms.remove(room.getSessions()); // ניקוי חדר ריק
                }
            }
        }
        if (player != null) {
            System.out.println("Player disconnected: " + player.getUsername());
        }
    }
}