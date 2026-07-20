package org.example.network;

import org.example.models.Position;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChessWebSocketHandler extends TextWebSocketHandler {

    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, GameRoom> sessionToRoom = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, PlayerInfo> players = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AuthHandler authHandler = new AuthHandler(objectMapper); // המחלקה החדשה

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();

        if (payload == null || payload.isEmpty()) return;

        // 1. טיפול בבקשת הצטרפות/התחברות JSON דרך ה-AuthHandler
        if (payload.charAt(0) == '{') {
            authHandler.processJoinRequest(session, payload, rooms, sessionToRoom, players);
            return;
        }

        // 2. וידוא שהשחקן מחובר לחדר פעיל
        PlayerInfo player = players.get(session);
        GameRoom room = sessionToRoom.get(session);
        if (player == null || room == null || !room.isStarted()) {
            return;
        }

        // 3. ניתוק פקודות תנועה וקפיצה
        if (Character.toUpperCase(payload.charAt(0)) == 'J') {
            handleJumpCommand(room, player, payload);
        } else {
            handleMoveCommand(room, player, payload);
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

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) throws Exception {
        PlayerInfo player = players.remove(session);
        GameRoom room = sessionToRoom.remove(session);

        if (room != null) {
            synchronized (room) {
                room.getSessions().remove(session);
                if (room.getSessions().isEmpty()) {
                    rooms.values().remove(room);
                }
            }
        }
        if (player != null) {
            System.out.println("🔌 Player disconnected: " + player.getUsername());
        }
    }
}