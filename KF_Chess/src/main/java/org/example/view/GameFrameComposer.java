package org.example.view;

import org.example.bus.GameEventBus;
import org.example.bus.GameServerEvents;
import org.example.engines.GameHistoryManager;
import org.example.engines.GameSnapshot;

import javax.swing.Timer;
import java.util.function.Consumer;

public class GameFrameComposer {
    private static final int RENDER_FPS = 30;
    private static final int RENDER_INTERVAL_MS = 1000 / RENDER_FPS;

    private final ImgRenderer boardRenderer;
    private final GameHistoryManager historyManager;
    private final BoardGeometry geometry;
    private final ScoreManager scoreManager;

    private final BoardLayoutCalculator layoutCalculator = new BoardLayoutCalculator();
    private final FrameRenderer frameRenderer;

    private volatile BoardLayoutCalculator.Metrics lastLayout;

    private volatile GameSnapshot lastSnapshot;
    private volatile int lastWindowWidth;
    private volatile int lastWindowHeight;

    private final Timer renderTimer;

    private final String username1;
    private final String username2;

    // Kept as fields so they can be unsubscribed on game-over -- see
    // shutdown(). GameEventBus.unsubscribe() requires the exact same
    // Consumer instance you subscribed with; a fresh lambda won't match.
    private final Consumer<Object> onBoardUpdate;
    private final Consumer<Object> onPieceCaptured;
    private final Consumer<Object> onMoveLogged;
    private final Consumer<Object> onGameOver;

    public GameFrameComposer(ImgRenderer boardRenderer, GameHistoryManager historyManager,
                             BoardGeometry geometry, ScoreManager scoreManager, String username1, String username2) {
        this.boardRenderer = boardRenderer;
        this.historyManager = historyManager;
        this.geometry = geometry;
        this.scoreManager = scoreManager;
        this.frameRenderer = new FrameRenderer(geometry);
        this.username1 = username1;
        this.username2 = username2;

        this.onBoardUpdate = data -> {
            BoardUpdatePayload payload = (BoardUpdatePayload) data;
            lastSnapshot = payload.snapshot();
            lastWindowWidth = payload.windowWidth();
            lastWindowHeight = payload.windowHeight();
        };

        this.onPieceCaptured = data -> {
            Object[] captureData = (Object[]) data;
            char capturedType = (char) captureData[0];
            char capturingColor = (char) captureData[1];
            scoreManager.onPieceCaptured(capturedType, capturingColor);
        };

        this.onMoveLogged = data -> {
            Object[] moveData = (Object[]) data;
            String time = (String) moveData[0];
            String move = (String) moveData[1];
            char color = (char) moveData[2];
            historyManager.onMoveAdded(time, move, color);
        };

        // Ends this composer's lifecycle: stop the render loop and detach
        // every listener from the bus so this instance (and everything it
        // closes over) becomes eligible for GC instead of staying pinned
        // forever by GameEventBus's internal listener lists.
        this.onGameOver = data -> shutdown();

        GameEventBus.getInstance().subscribe(GameServerEvents.PIECE_CAPTURED, onPieceCaptured);
        GameEventBus.getInstance().subscribe(GameServerEvents.MOVE_LOGGED, onMoveLogged);
        GameEventBus.getInstance().subscribe(GameServerEvents.GAME_OVER, onGameOver);
        GameEventBus.getInstance().subscribe(GameServerEvents.BOARD_UPDATE, onBoardUpdate);

        this.renderTimer = new Timer(RENDER_INTERVAL_MS, e -> {
            GameSnapshot snapshot = lastSnapshot;
            if (snapshot == null) return;
            Img newFrame = composeFrame(snapshot, lastWindowWidth, lastWindowHeight);
            GameEventBus.getInstance().publish(GameServerEvents.FRAME_READY, newFrame);
        });
        this.renderTimer.start();
    }

    /** Stops rendering and detaches every listener this composer registered. Safe to call more than once. */
    public void shutdown() {
        renderTimer.stop();
        GameEventBus.getInstance().unsubscribe(GameServerEvents.PIECE_CAPTURED, onPieceCaptured);
        GameEventBus.getInstance().unsubscribe(GameServerEvents.MOVE_LOGGED, onMoveLogged);
        GameEventBus.getInstance().unsubscribe(GameServerEvents.GAME_OVER, onGameOver);
    }

    public record BoardUpdatePayload(GameSnapshot snapshot, int windowWidth, int windowHeight) {}

    public int getBoardX() { return lastLayout != null ? lastLayout.boardX() : 0; }
    public int getBoardY() { return lastLayout != null ? lastLayout.boardY() : 0; }

    public Img composeFrame(GameSnapshot snapshot, int currentWindowWidth, int currentWindowHeight) {
        long frameTime = System.currentTimeMillis();
        BoardLayoutCalculator.Metrics layout = layoutCalculator.calculate(currentWindowWidth, currentWindowHeight);
        geometry.updateSize(layout.boardSize());
        this.lastLayout = layout;

        Img masterFrame = new Img().createEmpty(layout.windowWidth(), layout.windowHeight(), true);

        frameRenderer.drawBackground(masterFrame, layout.windowWidth(), layout.windowHeight());

        Img boardImg = boardRenderer.drawGame(snapshot, frameTime);
        boardImg.drawOn(masterFrame, layout.boardX(), layout.boardY());

        frameRenderer.drawColumnLabels(masterFrame, layout, true);
        frameRenderer.drawColumnLabels(masterFrame, layout, false);
        frameRenderer.drawRowLabels(masterFrame, layout);

        frameRenderer.drawPlayerPanel(masterFrame, username2 + " - Black", scoreManager.getBlackScore(), historyManager.getBlackMoves(),
                layout.leftPanelX(), layout.boardY(), layout.boardSize(), layout.panelWidth());
        frameRenderer.drawPlayerPanel(masterFrame, username1 + " - White", scoreManager.getWhiteScore(), historyManager.getWhiteMoves(),
                layout.rightPanelX(), layout.boardY(), layout.boardSize(), layout.panelWidth());

        return masterFrame;
    }
}