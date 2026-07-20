package org.example.network;

import org.example.engines.GameEngine;
import org.example.engines.GameSnapshot;
import org.example.models.Position;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ChessWebSocketHandler extends TextWebSocketHandler {

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // הזרקה או אתחול של ה-GameEngine שלך (כאן הוא מנוהל בשרת)
    private final GameEngine gameEngine;

    public ChessWebSocketHandler(GameEngine gameEngine) {
        this.gameEngine = gameEngine;

        // נרשמים לאירועים של המנוע כדי להפיץ אותם לרשת ברגע שהם קורים!
        this.gameEngine.addCaptureListener((capturedType, capturingColor) -> {
            broadcastSafe("{\"type\":\"PIECE_CAPTURED\",\"data\":[\"" + capturedType + "\",\"" + capturingColor + "\"]}");
        });

        this.gameEngine.addMoveListener((time, moveNotation, color) -> {
            broadcastSafe("{\"type\":\"MOVE_LOGGED\",\"data\":[\"" + time + "\",\"" + moveNotation + "\",\"" + color + "\"]}");
        });
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        // שולחים מיד את מצב הלוח הנוכחי לשחקן שהתחבר
        sendGameState(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String command = message.getPayload(); // move: "WQe2e5"  |  jump: "JWe4"

        if (command == null || command.isEmpty()) {
            System.err.println("פקודה ריקה מהלקוח, מתעלמים.");
            return;
        }

        if (Character.toUpperCase(command.charAt(0)) == 'J') {
            handleJumpCommand(command);
        } else {
            handleMoveCommand(command);
        }
    }

    private void handleMoveCommand(String command) {
        if (!isWellFormedMoveCommand(command)) {
            System.err.println("פקודת מהלך לא תקינה מהלקוח, מתעלמים: " + command);
            return;
        }

        try {
            char colorChar = command.charAt(0); // 'W' / 'B'
            Position from = parseNotation(command.substring(2, 4));
            Position to = parseNotation(command.substring(4, 6));

            // 🔒 אכיפת בעלות: מוודאים שהכלי ב"from" באמת שייך לצבע שהחתום על הפקודה,
            // כדי שלקוח אחד לא יוכל להזיז כלים של יריב.
            org.example.models.Piece piece = gameEngine.getPieceAt(from);
            char expectedColor = Character.toUpperCase(colorChar) == 'W' ? 'w' : 'b';
            if (piece == null || piece.getColor() != expectedColor) {
                System.err.println("ניסיון להזיז כלי שלא שייך לשולח, מתעלמים: " + command);
                return;
            }

            // הפעלת המהלך האמיתי בתוך ה-GameEngine שלך!
            gameEngine.requestMove(from, to);
        } catch (Exception e) {
            System.err.println("שגיאה בעיבוד פקודת מהלך '" + command + "': " + e.getMessage());
        }

        // 🛑 Don't send state here - let the game loop handle it
        // שליחת ה-Snapshot המעודכן לכולם
        // sendGameStateToAll();
    }

    private void handleJumpCommand(String command) {
        if (!isWellFormedJumpCommand(command)) {
            System.err.println("פקודת קפיצה לא תקינה מהלקוח, מתעלמים: " + command);
            return;
        }

        try {
            char colorChar = command.charAt(1); // 'W' / 'B'
            Position destination = parseNotation(command.substring(2, 4));

            // 🔒 אותה אכיפת בעלות כמו במהלך רגיל - אי אפשר לגרום לכלי של היריב לקפוץ.
            org.example.models.Piece piece = gameEngine.getPieceAt(destination);
            char expectedColor = Character.toUpperCase(colorChar) == 'W' ? 'w' : 'b';
            if (piece == null || piece.getColor() != expectedColor) {
                System.err.println("ניסיון להקפיץ כלי שלא שייך לשולח, מתעלמים: " + command);
                return;
            }

            gameEngine.jumpRequest(destination);
        } catch (Exception e) {
            System.err.println("שגיאה בעיבוד פקודת קפיצה '" + command + "': " + e.getMessage());
        }
    }

    // בדיקת תקינות בסיסית למהלך: 6 תווים, קוד צבע תקין,
    // ושתי משבצות בפורמט אות(a-h)+ספרה(1-8).
    private boolean isWellFormedMoveCommand(String command) {
        if (command.length() != 6) {
            return false;
        }
        char colorChar = Character.toUpperCase(command.charAt(0));
        if (colorChar != 'W' && colorChar != 'B') {
            return false;
        }
        return isValidSquare(command.substring(2, 4)) && isValidSquare(command.substring(4, 6));
    }

    // בדיקת תקינות בסיסית לקפיצה: 4 תווים - 'J' + קוד צבע + משבצת.
    private boolean isWellFormedJumpCommand(String command) {
        if (command.length() != 4) {
            return false;
        }
        char colorChar = Character.toUpperCase(command.charAt(1));
        if (colorChar != 'W' && colorChar != 'B') {
            return false;
        }
        return isValidSquare(command.substring(2, 4));
    }

    private boolean isValidSquare(String square) {
        if (square.length() != 2) return false;
        char file = Character.toLowerCase(square.charAt(0));
        char rank = square.charAt(1);
        return file >= 'a' && file <= 'h' && rank >= '1' && rank <= '8';
    }

    public void sendGameStateToAll() throws Exception {
        // שימוש במתודה המקורית שלך מהקוד!
        GameSnapshot snapshot = gameEngine.getSnapshot();
        String snapshotJson = objectMapper.writeValueAsString(snapshot);
        String response = "{\"type\":\"BOARD_UPDATE\",\"snapshot\":" + snapshotJson + "}";

        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(response));
            }
        }
    }

    private void sendGameState(WebSocketSession session) throws Exception {
        GameSnapshot snapshot = gameEngine.getSnapshot();
        String snapshotJson = objectMapper.writeValueAsString(snapshot);
        String response = "{\"type\":\"BOARD_UPDATE\",\"snapshot\":" + snapshotJson + "}";
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(response));
        }
    }

    private void broadcastSafe(String messageText) {
        TextMessage msg = new TextMessage(messageText);
        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) session.sendMessage(msg);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // פונקציית עזר להפיכת תו כמו 'e2' ל-Position(row, col) לפי לוח שחמט סטנדרטי
    private Position parseNotation(String notation) {
        int col = notation.charAt(0) - 'a';            // 'e' -> 4
        int row = 8 - Character.getNumericValue(notation.charAt(1)); // '2' -> שורה 6 (תלוי במבנה הלוח שלך)
        return new Position(row, col);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) throws Exception {
        sessions.remove(session);
    }
}