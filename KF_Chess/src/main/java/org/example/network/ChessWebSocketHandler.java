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
        String command = message.getPayload(); // למשל: "WQe2e5"

        if (command.length() == 6) {
            // תרגום המחרוזת (e2, e5) למיקומי לוח (שורות ועמודות)
            Position from = parseNotation(command.substring(2, 4));
            Position to = parseNotation(command.substring(4, 6));

            // הפעלת המהלך האמיתי בתוך ה-GameEngine שלך!
            gameEngine.requestMove(from, to);
        }

        // 🛑 Don't send state here - let the game loop handle it
        // שליחת ה-Snapshot המעודכן לכולם
        // sendGameStateToAll();
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