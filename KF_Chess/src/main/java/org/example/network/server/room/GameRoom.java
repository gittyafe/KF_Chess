package org.example.network.server.room;

import lombok.extern.slf4j.Slf4j;
import org.example.database.RatingService;
import org.example.engines.BoardLoader;
import org.example.engines.GameEngine;
import org.example.models.Board;
import org.example.network.server.game.DisconnectCountdownManager;
import org.example.network.server.game.GameLoopRunner;
import org.example.realtime.RealTimeArbiter;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class GameRoom {

    public enum JoinRole { WHITE, BLACK, SPECTATOR }
    public static final String PATH_BOARD_STARTING_POSITION = "/board.csv";

    public record JoinResult(JoinRole role, char color) {
        public static JoinResult of(JoinRole role) {
            char c = switch (role) {
                case WHITE -> 'W';
                case BLACK -> 'B';
                default -> '-';
            };
            return new JoinResult(role, c);
        }
    }

    private final String roomId;
    private final GameEngine gameEngine;
    private final RoomPlayers players = new RoomPlayers();
    private final RoomMessenger messenger;
    private final GameLoopRunner loopRunner;
    private final DisconnectCountdownManager disconnectManager;
    private final RatingService ratingService = new RatingService();

    // The dirty flag is set to true whenever the board state or timer changes, indicating that a network update is needed.
    private volatile boolean dirty = true;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean gameEnded = new AtomicBoolean(false);
    private Runnable onEndedCallback;

    private static final int BOARD_WIDTH = 8;
    private static final int BOARD_HEIGHT = 8;

    public void setOnEnded(Runnable callback) {
        this.onEndedCallback = callback;
    }

    /**
     * Call this whenever the board state or timer changes to request a network update.
     */
    public void markDirty() {
        this.dirty = true;
    }

    public GameRoom(String roomId) {
        this.roomId = roomId;

        Board board = new Board(BOARD_WIDTH, BOARD_HEIGHT);
        BoardLoader.loadFromClasspath(board, PATH_BOARD_STARTING_POSITION);
        this.gameEngine = new GameEngine(board, new RealTimeArbiter());

        this.messenger = new RoomMessenger(players, gameEngine);
        this.loopRunner = new GameLoopRunner(scheduler, this::tick);
        this.disconnectManager = new DisconnectCountdownManager(scheduler, messenger,
                (winner, loser) -> endGame(winner, loser, "RESIGN_DISCONNECT"));

        gameEngine.addCaptureListener((capturedType, capturingColor) -> {
            markDirty(); // שינוי בלוח (אכילה) -> מסמנים dirty!
            messenger.broadcastEvent("PIECE_CAPTURED", List.of(capturedType, capturingColor));

            if (gameEngine.isGameOver()) {
                boolean whiteCaptured = (capturingColor == 'W' || capturingColor == 'w');
                String winner = whiteCaptured ? players.getWhiteUsername() : players.getBlackUsername();
                String loser = whiteCaptured ? players.getBlackUsername() : players.getWhiteUsername();
                endGame(winner, loser, "CHECKMATE");
            }
        });

        gameEngine.addMoveListener((time, moveNotation, color) -> {
            markDirty(); // שינוי בלוח (מהלך) -> מסמנים dirty!
            messenger.broadcastEvent("MOVE_LOGGED", List.of(time, moveNotation, color));
        });
    }

    public String getRoomId() { return roomId; }

    public synchronized JoinResult addPlayer(WebSocketSession session, String username) {
        JoinResult result = players.addPlayer(session, username, roomId);

        switch (result.role()) {
            case BLACK -> {
                messenger.broadcastGameStarted();
                markDirty(); // משדרים את המצב ההתחלתי מיד עם תחילת המשחק
                startLoop();
            }
            case SPECTATOR -> {
                if (players.isStarted()) {
                    messenger.sendGameStateTo(session);
                }
            }
            default -> { /* WHITE: nothing more to do until black joins */ }
        }
        return result;
    }

    /**
     * The periodic 30ms tick callback. Now event-driven!
     */
    private void tick() {
        if (gameEngine.isGameOver() || !players.isRoomActive()) {
            return;
        }

        // 1. מעדכנים את לוגיקת המשחק/הזמנים
        gameEngine.wait_((int) GameLoopRunner.TICK_MS);

        // 2. משדרים ברשת אך ורק אם הדגל dirty דלוק!
        if (dirty) {
            dirty = false; // איפוס הדגל לאחר השידור
            messenger.broadcastGameState();
        }
    }

    public void startLoop() { loopRunner.start(roomId); }

    public void stopLoop() { loopRunner.stop(roomId); }

    public void shutdown() {
        stopLoop();
        disconnectManager.cancel();
        scheduler.shutdown();
    }

    public void endGame(String winner, String loser, String reason) {
        if (!gameEnded.compareAndSet(false, true)) return;

        log.info("[SERVER OUT] GAME OVER in room [{}]. Winner: {} ({})", roomId, winner, reason);

        if (winner != null && loser != null) {
            ratingService.applyGameResultAsync(players.getWhiteUsername(), players.getBlackUsername(),
                    winner.equals(players.getWhiteUsername()) ? 1.0 : 0.0);
        }

        messenger.broadcastGameOver(winner, reason);

        shutdown();

        if (onEndedCallback != null) {
            try {
                onEndedCallback.run();
            } catch (Exception e) {
                log.error("[SERVER ERROR] Error in room onEnded callback: {}", e.getMessage());
            }
        }
    }

    public void sendGameStateToAll() { messenger.broadcastGameState(); }

    public void broadcastEvent(String type, List<Object> data) { messenger.broadcastEvent(type, data); }

    public void broadcast(String messageText) { messenger.broadcastRaw(messageText); }

    public synchronized void handlePlayerDisconnect(WebSocketSession session) {
        if (!players.isStarted() || gameEnded.get()) return;

        String winner = players.opponentUsernameFor(session);
        String loser = players.usernameFor(session);
        disconnectManager.startCountdown(winner, loser);
    }

    public void cancelDisconnectTimer() { disconnectManager.cancel(); }

    public synchronized boolean reconnectPlayer(WebSocketSession newSession, String username) {
        if (isEnded()) return false;

        boolean rebound = players.reconnect(newSession, username);
        if (!rebound) return false;

        log.info("[SERVER OUT] Player {} reconnected to room [{}]", username, roomId);
        cancelDisconnectTimer();
        markDirty(); // סימון dirty כדי שעדכון יישלח ללקוח שהתחבר מחדש
        messenger.sendGameStateTo(newSession);
        return true;
    }

    public boolean isSpectator(WebSocketSession session) {
        return players.isSpectator(session);
    }

    public char getColorForUsername(String username) { return players.getColorForUsername(username); }

    public void removeSession(WebSocketSession session) { players.removeSession(session); }

    public GameEngine getGameEngine() { return gameEngine; }
    public boolean isStarted() { return players.isStarted(); }
    public boolean isEnded() { return gameEnded.get(); }
    public String getWhiteUsername() { return players.getWhiteUsername(); }
    public String getBlackUsername() { return players.getBlackUsername(); }
    public List<WebSocketSession> getSessions() { return players.getSessions(); }
}