package org.example.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.engines.GameEngine;
import org.example.engines.GameSnapshot;
import org.example.models.Board;
import org.example.models.Piece;
import org.example.models.PieceFactory;
import org.example.models.Position;
import org.example.network.NetworkDTOs.GameStartedResponse;
import org.example.network.NetworkDTOs.SimpleEventResponse;
import org.example.realtime.RealTimeArbiter;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class GameRoom {

    private final String roomId;
    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final GameEngine gameEngine;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ScheduledExecutorService gameLoopExecutor = Executors.newSingleThreadScheduledExecutor();

    private WebSocketSession whiteSession;
    private String whiteUsername;
    private WebSocketSession blackSession;
    private String blackUsername;
    private volatile boolean isStarted = false;
    private ScheduledFuture<?> loopHandle;

    public GameRoom(String roomId) {
        this.roomId = roomId;

        Board board = new Board(8, 8);
        RealTimeArbiter rta = new RealTimeArbiter();
        loadBoardFromClasspath(board, "/board.csv");
        this.gameEngine = new GameEngine(board, rta);

        addListenerCuptured();

        this.gameEngine.addMoveListener((time, moveNotation, color) -> {
            broadcastEvent("MOVE_LOGGED", List.of(time, moveNotation, color));
        });
    }
    public void addListenerCuptured(){
        this.gameEngine.addCaptureListener((capturedType, capturingColor) -> {
            // 1. שידור אירוע אכילה ללקוחות (לסאונד ועדכון ניקוד UI)
            broadcastEvent("PIECE_CAPTURED", List.of(capturedType, capturingColor));

            // 2. אם המשחק הסתיים (למשל המלך נאכל / שח-מט)
            if (gameEngine.isGameOver()) {
                // 🟢 א. זיהוי נכון של המנצח והמפסיד
                String winner = (capturingColor == 'W' || capturingColor == 'w') ? whiteUsername : blackUsername;
                String loser = winner.equals(whiteUsername) ? blackUsername : whiteUsername;

                System.out.println("🏆 GAME OVER in Room [" + roomId + "]! Winner: " + winner);

                // 🟢 ב. עדכון ELO בבסיס הנתונים
                try {
                    org.example.database.DatabaseManager.updateUserRating(winner, 15);
                    org.example.database.DatabaseManager.updateUserRating(loser, -15);
                } catch (Exception e) {
                    System.err.println("⚠️ Failed to update DB ratings: " + e.getMessage());
                }

                // 🟢 ג. שידור GAME_OVER בפורמט אחיד עם שם המנצח
                try {
                    String gameOverJson = objectMapper.writeValueAsString(Map.of(
                            "type", "GAME_OVER",
                            "winner", winner,
                            "reason", "CHECKMATE"
                    ));
                    broadcast(gameOverJson);
                } catch (Exception e) {
                    System.err.println("❌ Error sending GAME_OVER: " + e.getMessage());
                }

                // 🟢 ד. עצירת ה-Game Loop בשרת
                stopLoop();
            }
        });
    }

    public synchronized boolean addPlayer(WebSocketSession session, String username) {
        sessions.add(session);

        if (whiteSession == null) {
            whiteSession = session;
            whiteUsername = username;
            System.out.println("👤 Player 1 (White) joined room [" + roomId + "]: " + username);
        } else if (blackSession == null) {
            blackSession = session;
            blackUsername = username;
            isStarted = true;
            System.out.println("👤 Player 2 (Black) joined room [" + roomId + "]: " + username);

            broadcastGameStarted();
            startLoop();
        } else {
            System.out.println("👁️ Spectator joined room [" + roomId + "]: " + username);
            if (isStarted) {
                sendGameStateToSession(session);
            }
        }
        return true;
    }

    private void broadcastGameStarted() {
        try {
            GameStartedResponse response = new GameStartedResponse(whiteUsername, blackUsername);
            broadcast(objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            System.err.println("Error broadcasting GAME_STARTED: " + e.getMessage());
        }
    }

    private void sendGameStateToSession(WebSocketSession session) {
        CompletableFuture.runAsync(() -> {
            try {
                String gameStartedJson = objectMapper.writeValueAsString(new GameStartedResponse(whiteUsername, blackUsername));
                session.sendMessage(new TextMessage(gameStartedJson));

                Thread.sleep(100);

                GameSnapshot snapshot = gameEngine.getSnapshot();
                String snapshotJson = objectMapper.writeValueAsString(Map.of("type", "BOARD_UPDATE", "snapshot", snapshot));
                session.sendMessage(new TextMessage(snapshotJson));
            } catch (Exception e) {
                System.err.println("Error sending state to spectator: " + e.getMessage());
            }
        });
    }

    public synchronized void startLoop() {
        if (loopHandle != null && !loopHandle.isDone()) return;

        System.out.println("🚀 Room [" + roomId + "] Game Loop Started!");
        loopHandle = gameLoopExecutor.scheduleAtFixedRate(() -> {
            try {
                if (gameEngine.isGameOver() || !isRoomActive()) {
                    stopLoop();
                    return;
                }
                gameEngine.wait_(30);
                sendGameStateToAll();
            } catch (Exception e) {
                System.err.println("Error in room loop [" + roomId + "]: " + e.getMessage());
            }
        }, 0, 30, TimeUnit.MILLISECONDS);
    }

    public void stopLoop() {
        if (loopHandle != null) {
            loopHandle.cancel(false);
            gameLoopExecutor.shutdown();
            System.out.println("🏁 Room [" + roomId + "] Game Loop Ended.");
        }
    }

    public void sendGameStateToAll() throws Exception {
        GameSnapshot snapshot = gameEngine.getSnapshot();
        String json = objectMapper.writeValueAsString(Map.of("type", "BOARD_UPDATE", "snapshot", snapshot));
        broadcast(json);
    }

    public void broadcastEvent(String type, List<Object> data) {
        try {
            SimpleEventResponse response = new SimpleEventResponse(type, data);
            broadcast(objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            System.err.println("Error broadcasting event " + type + ": " + e.getMessage());
        }
    }

    public void broadcast(String messageText) {
        TextMessage msg = new TextMessage(messageText);
        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(msg);
                }
            } catch (Exception ignored) {}
        }
    }

    private boolean isRoomActive() {
        return (whiteSession != null && whiteSession.isOpen()) || (blackSession != null && blackSession.isOpen());
    }

    private void loadBoardFromClasspath(Board board, String resourcePath) {
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                System.err.println("❌ CSV File not found in classpath: " + resourcePath);
                return;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                int rowIndex = 0;
                while ((line = reader.readLine()) != null && rowIndex < board.getHeight()) {
                    String[] cells = line.split(",", -1);
                    int colIndex = 0;
                    for (String cell : cells) {
                        if (colIndex >= board.getWidth()) break;
                        String trimmed = cell.trim();
                        if (trimmed.length() == 2) {
                            Position pos = new Position(rowIndex, colIndex);
                            Piece piece = PieceFactory.createPiece(trimmed.charAt(0), trimmed.charAt(1), pos);
                            board.addPiece(piece);
                        }
                        colIndex++;
                    }
                    rowIndex++;
                }
            }
        } catch (Exception e) {
            System.err.println("Error loading board CSV: " + e.getMessage());
        }
    }

    private java.util.concurrent.ScheduledFuture<?> disconnectTimer;

    public synchronized void handlePlayerDisconnect(WebSocketSession session) {
        if (!isStarted) return;

        String winner = (whiteSession == session) ? blackUsername : whiteUsername;
        broadcast("{\"type\":\"DISCONNECT_COUNTDOWN\",\"seconds\":20,\"winnerIfTimeout\":\"" + winner + "\"}");

        java.util.concurrent.ScheduledExecutorService scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        disconnectTimer = scheduler.schedule(() -> {
            synchronized (this) {
                System.out.println("⏰ Player timed out. Winner: " + winner);
                broadcast("{\"type\":\"GAME_OVER\",\"winner\":\"" + winner + "\",\"reason\":\"RESIGN_DISCONNECT\"}");
                stopLoop();
            }
        }, 20, java.util.concurrent.TimeUnit.SECONDS);
    }

    public synchronized void cancelDisconnectTimer() {
        if (disconnectTimer != null && !disconnectTimer.isDone()) {
            disconnectTimer.cancel(true);
            broadcast("{\"type\":\"DISCONNECT_CANCELLED\"}");
        }
    }

    public GameEngine getGameEngine() { return gameEngine; }
    public boolean isStarted() { return isStarted; }
    public String getWhiteUsername() { return whiteUsername; }
    public String getBlackUsername() { return blackUsername; }
    public List<WebSocketSession> getSessions() { return sessions; }
}