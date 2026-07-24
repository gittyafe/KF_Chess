package org.example.network;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

    private volatile String pendingUsername;
    private volatile String pendingPassword;

    private volatile String currentUsername;
    private volatile String currentPassword;

    // Remembered so we can transparently reconnect after a dropped socket.
    private volatile String serverUrl;
    // True once we've successfully logged in at least once -- distinguishes
    // "first connect, about to log in" from "we were mid-game and dropped".
    private volatile boolean hasLoggedInBefore = false;
    // Guards against piling up multiple concurrent reconnect-retry loops.
    private volatile boolean reconnecting = false;

    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "chess-reconnect");
        t.setDaemon(true);
        return t;
    });
    private static final int RECONNECT_RETRY_DELAY_SECONDS = 2;
    private static final int RECONNECT_MAX_ATTEMPTS = 10; // ~20s, matching the server's disconnect grace window

    public ChessWebSocketClient() {}

    public void connect(String serverUrl) {
        this.serverUrl = serverUrl;
        HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create(serverUrl), this)
                .thenAccept(ws -> this.webSocket = ws)
                .exceptionally(ex -> {
                    System.err.println("Failed to connect: " + ex.getMessage());
                    return null;
                });
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
            this.pendingUsername = username;
            this.pendingPassword = password;
        }
    }

    private void doSendLogin(String username, String password) {
        try {
            this.currentUsername = username;
            this.currentPassword = password;

            Map<String, String> payload = Map.of(
                    "type", "LOGIN",
                    "username", username,
                    "password", password
            );
            webSocket.sendText(objectMapper.writeValueAsString(payload), true);
        } catch (Exception e) {
            System.err.println("Error sending LOGIN request: " + e.getMessage());
        }
    }

    private void sendReconnect() {
        if (webSocket == null || webSocket.isOutputClosed() || currentUsername == null || currentPassword == null) {
            return;
        }
        try {
            Map<String, String> payload = Map.of(
                    "type", "RECONNECT",
                    "username", currentUsername,
                    "password", currentPassword
            );
            webSocket.sendText(objectMapper.writeValueAsString(payload), true);
            System.out.println("Sent RECONNECT for user: " + currentUsername);
        } catch (Exception e) {
            System.err.println("Error sending RECONNECT: " + e.getMessage());
        }
    }

    public void sendJoinRoom(String roomId) {
        sendJoinPayload("JOIN_ROOM", roomId);
    }

    public void sendJoinMatch(String roomId) {
        sendJoinPayload("JOIN_MATCH", roomId);
    }

    private void sendJoinPayload(String type, String roomId) {
        if (webSocket != null && !webSocket.isOutputClosed()) {
            try {
                Map<String, String> payload = Map.of(
                        "type", type,
                        "roomId", roomId,
                        "username", this.currentUsername != null ? this.currentUsername : "",
                        "password", this.currentPassword != null ? this.currentPassword : ""
                );
                webSocket.sendText(objectMapper.writeValueAsString(payload), true);
                System.out.println("Sent " + type + " for room: " + roomId);
            } catch (Exception e) {
                System.err.println("Error sending " + type + ": " + e.getMessage());
            }
        }
    }

    public void sendCreateRoom(String roomId) {
        if (webSocket != null && !webSocket.isOutputClosed()) {
            try {
                Map<String, String> payload = Map.of(
                        "type", "CREATE_ROOM",
                        "roomId", roomId,
                        "username", this.currentUsername != null ? this.currentUsername : "",
                        "password", this.currentPassword != null ? this.currentPassword : ""
                );
                webSocket.sendText(objectMapper.writeValueAsString(payload), true);
                System.out.println("Sent CREATE_ROOM for room: " + roomId);
            } catch (Exception e) {
                System.err.println("Error sending CREATE_ROOM: " + e.getMessage());
            }
        }
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        System.out.println("Connected to chess server.");
        this.webSocket = webSocket;
        this.reconnecting = false;

        if (pendingUsername != null && pendingPassword != null) {
            String username = pendingUsername;
            String password = pendingPassword;
            pendingUsername = null;
            pendingPassword = null;
            doSendLogin(username, password);
        } else if (hasLoggedInBefore && currentUsername != null && currentPassword != null) {
            // We've logged in before and the socket just re-opened without a
            // fresh login click -- this is a reconnect after a drop.
            sendReconnect();
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
                else if ("DISCONNECT_COUNTDOWN".equals(msgType)) {
                    int seconds = ((Number) root.get("seconds")).intValue();
                    System.out.println("Received DISCONNECT_COUNTDOWN from server: " + seconds + "s");
                    GameEventBus.getInstance().publish("DISCONNECT_COUNTDOWN", seconds);
                }
                else if ("DISCONNECT_CANCELLED".equals(msgType)) {
                    System.out.println("Received DISCONNECT_CANCELLED from server");
                    GameEventBus.getInstance().publish("DISCONNECT_CANCELLED", null);
                }
                else if ("RECONNECT_ACCEPTED".equals(msgType)) {
                    String username = (String) root.get("username");
                    char color = ((String) root.get("color")).charAt(0);
                    int rating = ((Number) root.get("rating")).intValue();
                    System.out.println("Reconnected successfully as " + username);
                    GameEventBus.getInstance().publish("RECONNECT_ACCEPTED", new Object[]{ username, color, rating });
                }
                else if ("RECONNECT_REJECTED".equals(msgType)) {
                    String reason = (String) root.get("reason");
                    System.err.println("Reconnect rejected: " + reason);
                    GameEventBus.getInstance().publish("RECONNECT_REJECTED", reason);
                }
                else if ("GAME_OVER".equals(msgType)) {
                    String winner = (String) root.get("winner");
                    String reason = (String) root.get("reason");
                    System.out.println("Game Over received. Winner: " + winner);
                    Object[] gameOverPayload = new Object[]{ winner, reason };
                    GameEventBus.getInstance().publish("GAME_OVER", gameOverPayload);
                }
                else if("CREATE_ACCEPTED".equals(msgType)) {
                    String username = (String) root.get("username");
                    GameEventBus.getInstance().publish("CREATE_ACCEPTED", username);
                }
                else if("CREATE_REJECTED".equals(msgType)) {
                    String reason = (String) root.get("reason");
                    GameEventBus.getInstance().publish("CREATE_REJECTED", reason);
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

                    System.out.println("Match found! Room: " + roomId + " against " + opponent);

                    sendJoinMatch(roomId);

                    Object[] matchPayload = new Object[]{ roomId, opponent };
                    GameEventBus.getInstance().publish("MATCH_FOUND", matchPayload);
                }
                else if ("LOGIN_SUCCESS".equals(msgType)) {
                    String username = (String) root.get("username");

                    Object colorObj = root.get("color");
                    char color = (colorObj != null) ? ((String) colorObj).charAt(0) : 'W';

                    Object ratingObj = root.get("rating");
                    int rating = (ratingObj != null) ? ((Number) ratingObj).intValue() : 1200;

                    hasLoggedInBefore = true;

                    Object[] joinPayload = new Object[]{ username, color, rating };
                    GameEventBus.getInstance().publish("LOGIN_SUCCESS", joinPayload);
                }

            } catch (Exception e) {
                System.err.println("Error processing network message: " + e.getMessage());
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
        System.out.println("Connection to server closed: " + reason);
        triggerAutoReconnect();
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        System.err.println("WebSocket error: " + error.getMessage());
        triggerAutoReconnect();
        WebSocket.Listener.super.onError(webSocket, error);
    }

    /** Only try to auto-reconnect if we'd actually logged in before -- otherwise
     *  this is just the initial (never-connected) state, or the user hasn't
     *  started a session yet. Safe to call from both onClose and onError since
     *  it's idempotent (guarded by `reconnecting`). */
    private synchronized void triggerAutoReconnect() {
        if (hasLoggedInBefore && currentUsername != null && !reconnecting) {
            reconnecting = true;
            scheduleReconnectAttempt(1);
        }
    }

    private void scheduleReconnectAttempt(int attempt) {
        if (attempt > RECONNECT_MAX_ATTEMPTS) {
            System.err.println("Giving up reconnecting after " + RECONNECT_MAX_ATTEMPTS + " attempts.");
            reconnecting = false;
            GameEventBus.getInstance().publish("RECONNECT_REJECTED", "Could not reach server");
            return;
        }
        reconnectScheduler.schedule(() -> {
            System.out.println("Reconnect attempt " + attempt + "/" + RECONNECT_MAX_ATTEMPTS + "...");
            if (webSocket != null && !webSocket.isOutputClosed()) {
                // Already reconnected via another path.
                reconnecting = false;
                return;
            }
            try {
                HttpClient.newHttpClient().newWebSocketBuilder()
                        .buildAsync(URI.create(serverUrl), this)
                        .thenAccept(ws -> this.webSocket = ws) // onOpen() fires sendReconnect()
                        .exceptionally(ex -> {
                            scheduleReconnectAttempt(attempt + 1);
                            return null;
                        });
            } catch (Exception e) {
                scheduleReconnectAttempt(attempt + 1);
            }
        }, RECONNECT_RETRY_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    public void sendFindMatch() {
        if (webSocket != null && !webSocket.isOutputClosed()) {
            try {
                Map<String, String> payload = Map.of("type", "FIND_MATCH");
                webSocket.sendText(objectMapper.writeValueAsString(payload), true);
            } catch (Exception e) {
                System.err.println("Error sending FIND_MATCH: " + e.getMessage());
            }
        }
    }

    public void sendCancelMatchmaking() {
        if (webSocket != null && !webSocket.isOutputClosed()) {
            try {
                Map<String, String> payload = Map.of("type", "CANCEL_MATCHMAKING");
                webSocket.sendText(objectMapper.writeValueAsString(payload), true);
            } catch (Exception e) {
                System.err.println("Error sending CANCEL_MATCHMAKING: " + e.getMessage());
            }
        }
    }
}
