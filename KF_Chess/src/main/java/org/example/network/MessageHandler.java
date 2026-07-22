package org.example.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.models.Piece;
import org.example.models.Position;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

public class MessageHandler {

    private final ObjectMapper objectMapper;
    private final AuthHandler authHandler;
    private final MatchmakingManager matchmakingManager;

    public MessageHandler(ObjectMapper objectMapper, AuthHandler authHandler, MatchmakingManager matchmakingManager) {
        this.objectMapper = objectMapper;
        this.authHandler = authHandler;
        this.matchmakingManager = matchmakingManager;
    }

    public void processMessage(
            WebSocketSession session,
            String payload,
            Map<String, GameRoom> rooms,
            Map<WebSocketSession, GameRoom> sessionToRoom,
            Map<WebSocketSession, PlayerInfo> players) {

        if (isJsonPayload(payload)) {
            handleJsonMessage(session, payload, rooms, sessionToRoom, players);
        } else {
            handleGameCommand(session, payload, sessionToRoom, players);
        }
    }

    private boolean isJsonPayload(String payload) {
        return payload.trim().startsWith("{");
    }

    private void handleJsonMessage(
            WebSocketSession session,
            String payload,
            Map<String, GameRoom> rooms,
            Map<WebSocketSession, GameRoom> sessionToRoom,
            Map<WebSocketSession, PlayerInfo> players) {
        try {
            Map<String, Object> root = objectMapper.readValue(payload, Map.class);
            String type = (String) root.get("type");
            if (type == null) return;

            switch (type) {
                case "LOGIN":
                    authHandler.processLoginRequest(session, payload, players);
                    break;
                case "JOIN":
                    authHandler.processJoinRequest(session, payload, rooms, sessionToRoom, players);
                    break;
                case "FIND_MATCH":
                    handleFindMatchRequest(session, players);
                    break;
                case "CANCEL_MATCHMAKING":
                    matchmakingManager.removeFromQueue(session);
                    sendResponse(session, "{\"type\":\"MATCHMAKING_CANCELLED\"}");
                    break;
                default:
                    System.err.println("⚠️ Unknown JSON message type: " + type);
            }
        } catch (Exception e) {
            System.err.println("❌ Error parsing JSON message: " + e.getMessage());
        }
    }

    private void handleFindMatchRequest(WebSocketSession session, Map<WebSocketSession, PlayerInfo> players) {
        PlayerInfo player = players.get(session);
        if (player != null) {
            int rating = org.example.database.DatabaseManager.getRating(player.username());
            matchmakingManager.addToQueue(session, player.username(), rating);
        } else {
            sendResponse(session, "{\"type\":\"MATCHMAKING_REJECTED\",\"reason\":\"Must be logged in to find match\"}");
        }
    }

    private void handleGameCommand(
            WebSocketSession session,
            String payload,
            Map<WebSocketSession, GameRoom> sessionToRoom,
            Map<WebSocketSession, PlayerInfo> players) {

        PlayerInfo player = players.get(session);
        GameRoom room = sessionToRoom.get(session);

        if (player == null || room == null || !room.isStarted()) return;

        char firstChar = Character.toUpperCase(payload.charAt(0));
        if (firstChar == 'J') {
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

            Piece piece = room.getGameEngine().getPieceAt(from);
            char expectedColor = player.color() == 'W' ? 'w' : 'b';

            if (piece != null && piece.getColor() == expectedColor) {
                room.getGameEngine().requestMove(from, to);
            }
        } catch (Exception e) {
            System.err.println("Error executing move: " + e.getMessage());
        }
    }

    private void handleJumpCommand(GameRoom room, PlayerInfo player, String command) {
        if (!isWellFormedJumpCommand(command)) return;

        try {
            Position destination = parseNotation(command.substring(2, 4));

            Piece piece = room.getGameEngine().getPieceAt(destination);
            char expectedColor = player.color() == 'W' ? 'w' : 'b';

            if (piece != null && piece.getColor() == expectedColor) {
                room.getGameEngine().jumpRequest(destination);
            }
        } catch (Exception e) {
            System.err.println("Error executing jump: " + e.getMessage());
        }
    }

    private boolean isWellFormedMoveCommand(String command) {
        if (command.length() != 6) return false;
        char colorChar = Character.toUpperCase(command.charAt(0));
        return (colorChar == 'W' || colorChar == 'B')
                && isValidSquare(command.substring(2, 4))
                && isValidSquare(command.substring(4, 6));
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

    private void sendResponse(WebSocketSession session, String text) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(text));
            }
        } catch (Exception e) {
            System.err.println("❌ Error sending WebSocket response: " + e.getMessage());
        }
    }
}
