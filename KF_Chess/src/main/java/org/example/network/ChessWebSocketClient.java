package org.example.network;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import org.example.bus.GameEventBus;
import org.example.engines.GameSnapshot;
import org.example.engines.PieceSnapshot;
import org.example.models.Position;
import org.example.models.State;
import org.example.view.GameFrameComposer.BoardUpdatePayload;
import org.example.view.GameWindow;

import com.fasterxml.jackson.databind.ObjectMapper;

public class ChessWebSocketClient implements WebSocket.Listener {

    private WebSocket webSocket;
    private final GameWindow gameWindow;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 🧱 בופר שיאסוף הודעות ארוכות שנחתכות ברשת
    private final StringBuilder messageBuffer = new StringBuilder();

    public ChessWebSocketClient(GameWindow gameWindow) {
        this.gameWindow = gameWindow;
    }

    public void connect(String serverUrl) {
        HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create(serverUrl), this)
                .thenAccept(ws -> this.webSocket = ws);
    }

    public void sendMoveCommand(String command) {
        if (webSocket != null && !webSocket.isOutputClosed()) {
            webSocket.sendText(command, true);
        }
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        System.out.println("🔗 מחובר לשרת השחמט בהצלחה!");
        WebSocket.Listener.super.onOpen(webSocket);
    }

    @SuppressWarnings("unchecked")
    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        messageBuffer.append(data);

        if (last) {
            String message = messageBuffer.toString();
            messageBuffer.setLength(0);

            try {
                Map<String, Object> root = objectMapper.readValue(message, Map.class);
                String msgType = (String) root.get("type");

                if ("BOARD_UPDATE".equals(msgType)) {
                    Map<String, Object> snapshotMap = (Map<String, Object>) root.get("snapshot");
                    GameSnapshot snapshot = parseSnapshotFromMap(snapshotMap);

                    // 🛑 If server sent empty pieces, ignore it (server not ready)
                    if (snapshot.pieces().isEmpty()) {
                        webSocket.request(1);
                        return null;
                    }

                    if (gameWindow != null) {
                        BoardUpdatePayload payload = new BoardUpdatePayload(
                                snapshot,
                                gameWindow.getWidth(),
                                gameWindow.getHeight()
                        );

                        javax.swing.SwingUtilities.invokeLater(() -> {
                            GameEventBus.getInstance().publish("BOARD_UPDATE", payload);
                        });
                    }
                }
                else if ("MOVE_LOGGED".equals(msgType)) {
                    List<Object> dataList = (List<Object>) root.get("data");
                    String time = (String) dataList.get(0);
                    String moveNotation = (String) dataList.get(1);
                    char color = ((String) dataList.get(2)).charAt(0);

                    Object[] movePayload = new Object[]{ time, moveNotation, color };
                    GameEventBus.getInstance().publish("MOVE_LOGGED", movePayload);
                }

            } catch (Exception e) {
                System.err.println("שגיאה בעיבוד הודעת רשת: " + e.getMessage());
                e.printStackTrace();
            }
        }

        webSocket.request(1); // בקשת המקטע הבא
        return null;
    }

    @SuppressWarnings("unchecked")
    private GameSnapshot parseSnapshotFromMap(Map<String, Object> snapshotMap) {
        List<PieceSnapshot> pieces = new ArrayList<>();
        List<Object> piecesList = (List<Object>) snapshotMap.get("pieces");

        for (Object item : piecesList) {
            Map<String, Object> pieceMap = (Map<String, Object>) item;

            int id = ((Number) pieceMap.get("id")).intValue();
            char type = ((String) pieceMap.get("type")).charAt(0);
            char color = ((String) pieceMap.get("color")).charAt(0);

            Map<String, Object> posMap = (Map<String, Object>) pieceMap.get("position");
            int row = ((Number) posMap.get("row")).intValue();
            int col = ((Number) posMap.get("column")).intValue();

            Map<String, Object> targetPosMap = (Map<String, Object>) pieceMap.get("targetPosition");
            int targetRow = ((Number) targetPosMap.get("row")).intValue();
            int targetCol = ((Number) targetPosMap.get("column")).intValue();

            State state = State.valueOf((String) pieceMap.get("state"));

            pieces.add(new PieceSnapshot(
                    id, type, color,
                    new Position(row, col),
                    new Position(targetRow, targetCol),
                    state
            ));
        }

        Boolean isGameOverObj = (Boolean) snapshotMap.get("isGameOver");
        if (isGameOverObj == null) {
            isGameOverObj = (Boolean) snapshotMap.get("gameOver");
        }
        boolean isGameOver = (isGameOverObj != null) ? isGameOverObj.booleanValue() : false;

        return new GameSnapshot(pieces, isGameOver);
    }

    @Override
    public java.util.concurrent.CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        System.out.println("🔌 החיבור לשרת נסגר: " + reason);
        return null;
    }
}