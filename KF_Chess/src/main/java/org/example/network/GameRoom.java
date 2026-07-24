package org.example.network;

import org.example.database.RatingService;
import org.example.engines.BoardLoader;
import org.example.engines.GameEngine;
import org.example.models.Board;
import org.example.realtime.RealTimeArbiter;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A single chess game "table". Owns the game engine and coordinates its
 * collaborators around one shared lifecycle, but deliberately doesn't
 * implement any of those concerns itself:
 *
 *  - {@link RoomPlayers}              who is seated / connected
 *  - {@link RoomMessenger}            what gets sent to whom
 *  - {@link GameLoopRunner}           the periodic board tick
 *  - {@link DisconnectCountdownManager} the reconnect grace period
 *  - {@link RatingService}            ELO changes when the game ends
 *
 * This class's own job is just sequencing: e.g. "when black joins, start
 * the loop and announce GAME_STARTED" or "when the game ends, stop the
 * loop, update ratings, and broadcast GAME_OVER, exactly once".
 */
public class GameRoom {

    public enum JoinRole { WHITE, BLACK, SPECTATOR }
    public static final String PATH_BOARD_STARTING_POSITION = "/board.csv";

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
    private final GameEngine gameEngine;
    private final RoomPlayers players = new RoomPlayers();
    private final RoomMessenger messenger;
    private final GameLoopRunner loopRunner;
    private final DisconnectCountdownManager disconnectManager;
    private final RatingService ratingService = new RatingService();

    // Shared by the tick loop and the disconnect countdown so a room only
    // ever needs one background thread (previously each disconnect spun up
    // its own ScheduledExecutorService that was never shut down).
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // Guards against the game being ended twice (e.g. a checkmate capture
    // racing with a disconnect timeout).
    private final AtomicBoolean gameEnded = new AtomicBoolean(false);

    // Invoked exactly once, when the game truly ends. Lets whoever's
    // tracking rooms (RoomRegistry) clean up without GameRoom needing to
    // know about that registry directly.
    private Runnable onEndedCallback;

    private static final int BOARD_WIDTH = 8;
    private static final int BOARD_HEIGHT = 8;

    public void setOnEnded(Runnable callback) {
        this.onEndedCallback = callback;
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
            messenger.broadcastEvent("PIECE_CAPTURED", List.of(capturedType, capturingColor));

            if (gameEngine.isGameOver()) {
                boolean whiteCaptured = (capturingColor == 'W' || capturingColor == 'w');
                String winner = whiteCaptured ? players.getWhiteUsername() : players.getBlackUsername();
                String loser = whiteCaptured ? players.getBlackUsername() : players.getWhiteUsername();
                endGame(winner, loser, "CHECKMATE");
            }
        });

        gameEngine.addMoveListener((time, moveNotation, color) ->
                messenger.broadcastEvent("MOVE_LOGGED", List.of(time, moveNotation, color)));
    }

    public String getRoomId() { return roomId; }

    /**
     * Adds a session to the room and decides its role. Single source of
     * truth for color assignment -- callers must not re-derive color
     * themselves.
     */
    public synchronized JoinResult addPlayer(WebSocketSession session, String username) {
        JoinResult result = players.addPlayer(session, username, roomId);

        switch (result.role()) {
            case BLACK -> {
                messenger.broadcastGameStarted();
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

    private void tick() {
        if (gameEngine.isGameOver() || !players.isRoomActive()) {
            return; // endGame()/disconnect path is responsible for stopping the loop
        }
        gameEngine.wait_((int) GameLoopRunner.TICK_MS);
        messenger.broadcastGameState();
    }

    public void startLoop() { loopRunner.start(roomId); }

    /** Stops the ticking board-update loop, but leaves the shared scheduler
     *  usable so the disconnect countdown can still run on it. */
    public void stopLoop() { loopRunner.stop(roomId); }

    /** Fully tears the room down -- call once the room is no longer needed. */
    public void shutdown() {
        stopLoop();
        disconnectManager.cancel();
        scheduler.shutdown();
    }

    /**
     * Single, idempotent path for ending a game: stops the loop, applies the
     * rating change exactly once, and broadcasts GAME_OVER exactly once --
     * regardless of whether the trigger was checkmate or a disconnect
     * timeout.
     */
    public void endGame(String winner, String loser, String reason) {
        if (!gameEnded.compareAndSet(false, true)) return; // already ended

        System.out.println("GAME OVER in room [" + roomId + "]. Winner: " + winner + " (" + reason + ")");

        if (winner != null && loser != null) {
            ratingService.applyGameResult(players.getWhiteUsername(), players.getBlackUsername(),
                    winner.equals(players.getWhiteUsername()) ? 1.0 : 0.0);
        }

        messenger.broadcastGameOver(winner, reason);

        // The game is genuinely finished now -- no more reconnecting into
        // it, so fully tear the room down (unlike a plain disconnect, where
        // we deliberately keep the loop/scheduler alive for a reconnect).
        shutdown();

        if (onEndedCallback != null) {
            try {
                onEndedCallback.run();
            } catch (Exception e) {
                System.err.println("Error in room onEnded callback: " + e.getMessage());
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

    /** Called once a disconnected player's session is rebound, to stop the pending resign-timeout. */
    public void cancelDisconnectTimer() { disconnectManager.cancel(); }

    /**
     * Rebinds a fresh WebSocketSession to an existing seat in this room,
     * identified by username. Returns false if the username isn't a
     * participant, or the game has already ended.
     */
    public synchronized boolean reconnectPlayer(WebSocketSession newSession, String username) {
        if (isEnded()) return false;

        boolean rebound = players.reconnect(newSession, username);
        if (!rebound) return false;

        System.out.println(username + " reconnected to room [" + roomId + "]");
        cancelDisconnectTimer();
        messenger.sendGameStateTo(newSession);
        return true;
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
