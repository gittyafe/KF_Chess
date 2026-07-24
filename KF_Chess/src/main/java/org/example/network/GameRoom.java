package org.example.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.database.DatabaseManager;
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
import java.util.concurrent.atomic.AtomicBoolean;

public class GameRoom {

    /**
     * Result of trying to add a session to the room. Replaces the old bare
     * boolean return, which was always {@code true} and let AuthHandler
     * independently (and incorrectly) guess a player's color.
     */
    public enum JoinRole { WHITE, BLACK, SPECTATOR }
    public static final int WINNER_ELO_CHANGE = 15;
    public static final int LOSER_ELO_CHANGE = -15;

    public record JoinResult(JoinRole role, char color) {
        public static JoinResult of(JoinRole role) {
            char c = switch (role) {
                case WHITE -> 'W';
                case BLACK -> 'B';
                default -> '-'; // spectators/rejects have no move authority
            };
            return new JoinResult(role, c);
        }
    }

    private final String roomId;
    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final GameEngine gameEngine;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Single shared scheduler for both the game loop and the disconnect
    // countdown. Previously each disconnect spun up a brand-new
    // ScheduledExecutorService that was never shut down (thread leak).
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private WebSocketSession whiteSession;
    private String whiteUsername;
    private WebSocketSession blackSession;
    private String blackUsername;
    private volatile boolean isStarted = false;

    // Guards against the game being ended twice (e.g. a checkmate capture
    // racing with a disconnect timeout) — without this, both paths could
    // broadcast GAME_OVER and double-apply ELO changes.
    private final AtomicBoolean gameEnded = new AtomicBoolean(false);

    private ScheduledFuture<?> loopHandle;
    private ScheduledFuture<?> disconnectTimer;

    // Invoked exactly once, when the game truly ends (checkmate or a
    // disconnect timeout with no reconnect). Lets ChessWebSocketHandler
    // clean up its `rooms` / `usernameToRoom` maps without GameRoom needing
    // to know about them directly.
    private Runnable onEndedCallback;

    public void setOnEnded(Runnable callback) {
        this.onEndedCallback = callback;
    }

    public GameRoom(String roomId) {
        this.roomId = roomId;

        Board board = new Board(8, 8);
        RealTimeArbiter rta = new RealTimeArbiter();
        loadBoardFromClasspath(board, "/board.csv");
        this.gameEngine = new GameEngine(board, rta);

        this.gameEngine.addCaptureListener((capturedType, capturingColor) -> {
            broadcastEvent("PIECE_CAPTURED", List.of(capturedType, capturingColor));

            if (gameEngine.isGameOver()) {
                boolean whiteCaptured = (capturingColor == 'W' || capturingColor == 'w');
                String winner = whiteCaptured ? whiteUsername : blackUsername;
                String loser = whiteCaptured ? blackUsername : whiteUsername;
                endGame(winner, loser, "CHECKMATE");
            }
        });

        this.gameEngine.addMoveListener((time, moveNotation, color) ->
                broadcastEvent("MOVE_LOGGED", List.of(time, moveNotation, color)));
    }

    public String getRoomId() { return roomId; }

    /**
     * Adds a session to the room and decides its role. This is now the
     * single source of truth for color assignment — callers (AuthHandler)
     * must not re-derive color themselves.
     */
    public synchronized JoinResult addPlayer(WebSocketSession session, String username) {
        if (whiteSession == null) {
            whiteSession = session;
            whiteUsername = username;
            sessions.add(session);
            System.out.println("Player 1 (White) joined room [" + roomId + "]: " + username);
            return JoinResult.of(JoinRole.WHITE);
        }

        if (blackSession == null) {
            blackSession = session;
            blackUsername = username;
            sessions.add(session);
            isStarted = true;
            System.out.println("Player 2 (Black) joined room [" + roomId + "]: " + username);

            broadcastGameStarted();
            startLoop();
            return JoinResult.of(JoinRole.BLACK);
        }

        // Room already has both players -> everyone else is a spectator,
        // never a colored participant with move authority.
        sessions.add(session);
        System.out.println("Spectator joined room [" + roomId + "]: " + username);
        if (isStarted) {
            sendGameStateToSession(session);
        }
        return JoinResult.of(JoinRole.SPECTATOR);
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
        if (scheduler.isShutdown() || (loopHandle != null && !loopHandle.isDone())) return;

        System.out.println("Room [" + roomId + "] Game Loop Started!");
        loopHandle = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (gameEngine.isGameOver() || !isRoomActive()) {
                    return; // endGame()/disconnect path is responsible for stopping the loop
                }
                gameEngine.wait_(30);
                sendGameStateToAll();
            } catch (Exception e) {
                System.err.println("Error in room loop [" + roomId + "]: " + e.getMessage());
            }
        }, 0, 30, TimeUnit.MILLISECONDS);
    }

    /** Stops the ticking board-update loop, but leaves the shared scheduler
     *  usable so the disconnect countdown can still run on it. */
    public synchronized void stopLoop() {
        if (loopHandle != null) {
            loopHandle.cancel(false);
            System.out.println("Room [" + roomId + "] Game Loop Ended.");
        }
    }

    /** Fully tears the room down — call once the room is no longer needed. */
    public synchronized void shutdown() {
        stopLoop();
        if (disconnectTimer != null) disconnectTimer.cancel(false);
        scheduler.shutdown();
    }

    /**
     * Single, idempotent path for ending a game: stops the loop, applies the
     * ELO change exactly once, and broadcasts GAME_OVER exactly once —
     * regardless of whether the trigger was checkmate or a disconnect
     * timeout.
     */
    public void endGame(String winner, String loser, String reason) {
        if (!gameEnded.compareAndSet(false, true)) return; // already ended

        System.out.println("GAME OVER in room [" + roomId + "]. Winner: " + winner + " (" + reason + ")");

        if (winner != null && loser != null) {
            try {
                DatabaseManager.updateRatings(whiteUsername, blackUsername, winner.equals(whiteUsername) ? 1.0 : 0.0);
//                EloCalculator.EloResult winnerResult = EloCalculator.calculateNewRatings(DatabaseManager.getRating(winner), DatabaseManager.getRating(loser), 1.0);
//                EloCalculator.EloResult loserResult = EloCalculator.calculateNewRatings(DatabaseManager.getRating(loser), DatabaseManager.getRating(winner), 0.0);
//                DatabaseManager.addUserRating(winner, (int)winnerResult.newRatingA);
//                DatabaseManager.addUserRating(loser, (int)loserResult.newRatingB);
            } catch (Exception e) {
                System.err.println("Failed to update DB ratings: " + e.getMessage());
            }
        }

        try {
            String json = objectMapper.writeValueAsString(Map.of(
                    "type", "GAME_OVER",
                    "winner", winner == null ? "" : winner,
                    "reason", reason));
            broadcast(json);
        } catch (Exception e) {
            System.err.println("Error sending GAME_OVER: " + e.getMessage());
        }

        // The game is genuinely finished now -- no more reconnecting into it,
        // so fully tear the room down (unlike a plain disconnect, where we
        // deliberately keep the loop/scheduler alive for a possible reconnect).
        shutdown();

        if (onEndedCallback != null) {
            try {
                onEndedCallback.run();
            } catch (Exception e) {
                System.err.println("Error in room onEnded callback: " + e.getMessage());
            }
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
                System.err.println("CSV File not found in classpath: " + resourcePath);
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

    public synchronized void handlePlayerDisconnect(WebSocketSession session) {
        if (!isStarted || gameEnded.get()) return;

        String winner = (whiteSession == session) ? blackUsername : whiteUsername;
        String loser = (whiteSession == session) ? whiteUsername : blackUsername;

        broadcast("{\"type\":\"DISCONNECT_COUNTDOWN\",\"seconds\":20,\"winnerIfTimeout\":\"" + winner + "\"}");

        disconnectTimer = scheduler.schedule(() -> {
            System.out.println("Player timed out. Winner: " + winner);
            endGame(winner, loser, "RESIGN_DISCONNECT");
        }, 20, TimeUnit.SECONDS);
    }

    /** Called by reconnectPlayer() once a disconnected player's session is
     *  rebound, to stop the pending resign-timeout and notify clients. */
    public synchronized void cancelDisconnectTimer() {
        if (disconnectTimer != null && !disconnectTimer.isDone()) {
            disconnectTimer.cancel(false);
            broadcast("{\"type\":\"DISCONNECT_CANCELLED\"}");
        }
    }

    /**
     * Rebinds a fresh WebSocketSession to an existing seat in this room,
     * identified by username. Used when a player reconnects during the
     * disconnect countdown (or any time before the game has ended).
     * Returns false if the username isn't a participant, or the game has
     * already ended.
     */
    public synchronized boolean reconnectPlayer(WebSocketSession newSession, String username) {
        if (isEnded()) return false;

        boolean isWhite = username.equalsIgnoreCase(whiteUsername);
        boolean isBlack = !isWhite && username.equalsIgnoreCase(blackUsername);
        if (!isWhite && !isBlack) return false;

        if (isWhite) {
            if (whiteSession != null) sessions.remove(whiteSession);
            whiteSession = newSession;
        } else {
            if (blackSession != null) sessions.remove(blackSession);
            blackSession = newSession;
        }
        sessions.add(newSession);

        System.out.println(username + " reconnected to room [" + roomId + "]");
        cancelDisconnectTimer();
        sendGameStateToSession(newSession);
        return true;
    }

    public char getColorForUsername(String username) {
        if (username.equalsIgnoreCase(whiteUsername)) return 'W';
        if (username.equalsIgnoreCase(blackUsername)) return 'B';
        return '-';
    }

    public GameEngine getGameEngine() { return gameEngine; }
    public boolean isStarted() { return isStarted; }
    public boolean isEnded() { return gameEnded.get(); }
    public String getWhiteUsername() { return whiteUsername; }
    public String getBlackUsername() { return blackUsername; }
    public List<WebSocketSession> getSessions() { return sessions; }
}
