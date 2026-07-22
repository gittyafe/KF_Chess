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

import com.fasterxml.jackson.databind.ObjectMapper;

public class ChessWebSocketClient implements WebSocket.Listener {

    private volatile WebSocket webSocket;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StringBuilder messageBuffer = new StringBuilder();

    // שמירת הנתונים במקרה והחיבור אסינכרוני וטרם נפתח ה-Socket
    private volatile String pendingUsername;
    private volatile String pendingPassword;
    private volatile String pendingRoomId;

    public ChessWebSocketClient() {}

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

    public void sendLogin(String username, String password) {
        if (webSocket != null && !webSocket.isOutputClosed()) {
            doSendLogin(username, password);
        } else {
            // 🟢 אם ה-Socket עדיין בלייב התחברות, נשמור את הפרטים ונשלח מיד כשייפתח (ב-onOpen)!
            this.pendingUsername = username;
            this.pendingPassword = password;
        }
    }

    // בתוך ChessWebSocketClient.java

    private volatile String currentUsername;
    private volatile String currentPassword;

    // עדכון המתודה doSendLogin שתשמור אותם:
    private void doSendLogin(String username, String password) {
        try {
            this.currentUsername = username; // 🟢 שמירת שם המשתמש
            this.currentPassword = password; // 🟢 שמירת הסיסמה

            Map<String, String> payload = Map.of(
                    "type", "LOGIN",
                    "username", username,
                    "password", password
            );
            webSocket.sendText(objectMapper.writeValueAsString(payload), true);
        } catch (Exception e) {
            System.err.println("❌ שגיאה בשליחת בקשת LOGIN: " + e.getMessage());
        }
    }

    public void sendJoinRoom(String roomId) {
        if (webSocket != null && !webSocket.isOutputClosed()) {
            try {
                // 🟢 בונים את ה-Payload כולל username ו-password
                Map<String, String> payload = Map.of(
                        "type", "JOIN",
                        "roomId", roomId,
                        "username", this.currentUsername != null ? this.currentUsername : "",
                        "password", this.currentPassword != null ? this.currentPassword : ""
                );

                webSocket.sendText(objectMapper.writeValueAsString(payload), true);
                System.out.println("📤 Sent JOIN with user details for room: " + roomId);
            } catch (Exception e) {
                System.err.println("❌ שגיאה בשליחת JOIN: " + e.getMessage());
            }
        }
    }

//    public void sendJoin(String username, String password, String roomId) {
//        if (webSocket != null) {
//            doSendJoin(username, password, roomId);
//        } else {
//            this.pendingUsername = username;
//            this.pendingPassword = password;
//            this.pendingRoomId = roomId;
//        }
//    }

    private void doSendJoin(String username, String password, String roomId) {
        try {
            Map<String, String> joinPayload = Map.of(
                    "type", "JOIN",
                    "username", username,
                    "password", password,
                    "roomId", roomId
            );
            webSocket.sendText(objectMapper.writeValueAsString(joinPayload), true);
        } catch (Exception e) {
            System.err.println("❌ שגיאה בשליחת הודעת JOIN לשרת: " + e.getMessage());
        }
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        System.out.println("🔗 מחובר לשרת השחמט בהצלחה!");
        this.webSocket = webSocket;

        // 🟢 אם המשתמש לחץ Login עוד לפני שהתחברנו סופית:
        if (pendingUsername != null && pendingPassword != null) {
            String username = pendingUsername;
            String password = pendingPassword;
            pendingUsername = null;
            pendingPassword = null;

            doSendLogin(username, password);
        }

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

                    if (snapshot.pieces().isEmpty()) {
                        webSocket.request(1);
                        return null;
                    }

                    GameEventBus.getInstance().publish("BOARD_UPDATE_RECEIVED", snapshot);
                }
                else if ("MOVE_LOGGED".equals(msgType)) {
                    List<Object> dataList = (List<Object>) root.get("data");
                    String time = (String) dataList.get(0);
                    String moveNotation = (String) dataList.get(1);
                    char color = ((String) dataList.get(2)).charAt(0);

                    Object[] movePayload = new Object[]{ time, moveNotation, color };
                    GameEventBus.getInstance().publish("MOVE_LOGGED", movePayload);
                }
                else if ("PIECE_CAPTURED".equals(msgType)) {
                    List<Object> dataList = (List<Object>) root.get("data");
                    char capturedType = ((String) dataList.get(0)).charAt(0);
                    char capturingColor = ((String) dataList.get(1)).charAt(0);

                    Object[] capturePayload = new Object[]{ capturedType, capturingColor };
                    GameEventBus.getInstance().publish("PIECE_CAPTURED", capturePayload);
                }
                else if ("JOIN_ACCEPTED".equals(msgType)) {
                    String username = (String) root.get("username");
                    char color = ((String) root.get("color")).charAt(0);
                    int rating = ((Number) root.get("rating")).intValue();

                    Object[] joinPayload = new Object[]{ username, color, rating };
                    GameEventBus.getInstance().publish("JOIN_ACCEPTED", joinPayload);
                }
                else if ("JOIN_REJECTED".equals(msgType)) {
                    String reason = (String) root.get("reason");
                    GameEventBus.getInstance().publish("JOIN_REJECTED", reason);
                }
                else if ("GAME_STARTED".equals(msgType)) {
                    List<Object> playersList = (List<Object>) root.get("data");
                    Object[] players = playersList.toArray();
                    GameEventBus.getInstance().publish("GAME_STARTED", players);
                }
                else if ("MATCHMAKING_STARTED".equals(msgType)) {
                    String msg = (String) root.get("message");
                    GameEventBus.getInstance().publish("MATCHMAKING_STARTED", msg);
                }
                else if ("MATCHMAKING_TIMEOUT".equals(msgType)) {
                    String reason = (String) root.get("reason");
                    GameEventBus.getInstance().publish("MATCHMAKING_TIMEOUT", reason);
                }
                else if ("MATCHMAKING_CANCELLED".equals(msgType)) {
                    GameEventBus.getInstance().publish("MATCHMAKING_CANCELLED", null);
                }
                else if ("MATCH_FOUND".equals(msgType)) {
                    String roomId = (String) root.get("roomId");
                    String opponent = (String) root.get("opponent");

                    System.out.println("⚔️ Match found! Room: " + roomId + " against " + opponent);

                    sendJoinRoom(roomId);

                    Object[] matchPayload = new Object[]{ roomId, opponent };
                    GameEventBus.getInstance().publish("MATCH_FOUND", matchPayload);
                }
                else if ("LOGIN_SUCCESS".equals(msgType)) {
                    String username = (String) root.get("username");

                    // אם השרת החזיר פרטים נוספים (כמו color או rating), נשמור אותם
                    Object colorObj = root.get("color");
                    char color = (colorObj != null) ? ((String) colorObj).charAt(0) : 'W';

                    Object ratingObj = root.get("rating");
                    int rating = (ratingObj != null) ? ((Number) ratingObj).intValue() : 1200;

                    Object[] joinPayload = new Object[]{ username, color, rating };

                    // 🟢 מפיצים את אירוע ההתחברות המוצלחת לכל מי שמקשיב ב-UI!
                    GameEventBus.getInstance().publish("LOGIN_SUCCESS", joinPayload);
                }

            } catch (Exception e) {
                System.err.println("❌ שגיאה בעיבוד הודעת רשת: " + e.getMessage());
                e.printStackTrace();
            }
        }

        webSocket.request(1);
        return null;
    }

    @SuppressWarnings("unchecked")
    private GameSnapshot parseSnapshotFromMap(Map<String, Object> snapshotMap) {
        List<PieceSnapshot> pieces = new ArrayList<>();
        List<Object> piecesList = (List<Object>) snapshotMap.get("pieces");

        if (piecesList != null) {
            for (Object item : piecesList) {
                Map<String, Object> pieceMap = (Map<String, Object>) item;

                int id = ((Number) pieceMap.get("id")).intValue();
                char type = ((String) pieceMap.get("type")).charAt(0);
                char color = ((String) pieceMap.get("color")).charAt(0);

                Map<String, Object> posMap = (Map<String, Object>) pieceMap.get("position");
                int row = ((Number) posMap.get("row")).intValue();
                int col = ((Number) posMap.get("column")).intValue();
                Position position = new Position(row, col);

                Map<String, Object> targetPosMap = (Map<String, Object>) pieceMap.get("targetPosition");
                int targetRow = ((Number) targetPosMap.get("row")).intValue();
                int targetCol = ((Number) targetPosMap.get("column")).intValue();
                Position targetPosition = new Position(targetRow, targetCol);

                State state = State.valueOf((String) pieceMap.get("state"));

                pieces.add(new PieceSnapshot(id, type, color, position, targetPosition, state));
            }
        }

        Boolean isGameOverObj = (Boolean) snapshotMap.get("isGameOver");
        if (isGameOverObj == null) {
            isGameOverObj = (Boolean) snapshotMap.get("gameOver");
        }
        boolean isGameOver = (isGameOverObj != null) ? isGameOverObj.booleanValue() : false;

        return new GameSnapshot(pieces, isGameOver);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        System.out.println("🔌 החיבור לשרת נסגר: " + reason);
        return null;
    }

    public void sendFindMatch() {
        if (webSocket != null && !webSocket.isOutputClosed()) {
            try {
                Map<String, String> payload = Map.of("type", "FIND_MATCH");
                webSocket.sendText(objectMapper.writeValueAsString(payload), true);
            } catch (Exception e) {
                System.err.println("❌ שגיאה בשליחת FIND_MATCH: " + e.getMessage());
            }
        }
    }

    public void sendCancelMatchmaking() {
        if (webSocket != null && !webSocket.isOutputClosed()) {
            try {
                Map<String, String> payload = Map.of("type", "CANCEL_MATCHMAKING");
                webSocket.sendText(objectMapper.writeValueAsString(payload), true);
            } catch (Exception e) {
                System.err.println("❌ שגיאה בשליחת CANCEL_MATCHMAKING: " + e.getMessage());
            }
        }
    }
}