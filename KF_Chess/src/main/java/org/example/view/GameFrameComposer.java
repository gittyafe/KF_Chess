package org.example.view;

import org.example.bus.GameEventBus;
import org.example.engines.GameHistoryManager;
import org.example.engines.GameSnapshot;

import javax.swing.Timer;

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

    // Cached "what to draw" -- updated whenever a BOARD_UPDATE arrives.
    // The render Timer below reads this on every tick, decoupling *when*
    // we redraw (a local clock, so idle animations stay smooth) from
    // *when the network told us something changed* (now correctly
    // sparse, per Server_Design.md's event-driven broadcast fix).
    private volatile GameSnapshot lastSnapshot;
    private volatile int lastWindowWidth;
    private volatile int lastWindowHeight;

    private final Timer renderTimer;

    private final String username1;
    private final String username2;

    public GameFrameComposer(ImgRenderer boardRenderer, GameHistoryManager historyManager,
                             BoardGeometry geometry, ScoreManager scoreManager, String username1, String username2) {
        this.boardRenderer = boardRenderer;
        this.historyManager = historyManager;
        this.geometry = geometry;
        this.scoreManager = scoreManager;
        this.frameRenderer = new FrameRenderer(geometry);
        this.username1 = username1;
        this.username2 = username2;

        GameEventBus.getInstance().subscribe("BOARD_UPDATE", data -> {
            BoardUpdatePayload payload = (BoardUpdatePayload) data;
            lastSnapshot = payload.snapshot();
            lastWindowWidth = payload.windowWidth();
            lastWindowHeight = payload.windowHeight();
        });

        GameEventBus.getInstance().subscribe("PIECE_CAPTURED", data -> {
            Object[] captureData = (Object[]) data;
            char capturedType = (char) captureData[0];
            char capturingColor = (char) captureData[1];
            scoreManager.onPieceCaptured(capturedType, capturingColor);
        });

        GameEventBus.getInstance().subscribe("MOVE_LOGGED", data -> {
            Object[] moveData = (Object[]) data;
            String time = (String) moveData[0];
            String move = (String) moveData[1];
            char color = (char) moveData[2];
            historyManager.onMoveAdded(time, move, color);
        });

        // Stop the local render loop once the game genuinely ends -- without
        // this, disposing GameWindow (frame.dispose() in
        // GameWindow.handleGameOver) does NOT stop this timer: dispose()
        // only releases native window resources, it doesn't trigger
        // EXIT_ON_CLOSE (that only fires on a user-initiated WINDOW_CLOSING
        // event). Left unhandled, the 30fps render Timer would keep firing
        // and publishing FRAME_READY into the void for the rest of the
        // process's life, for every game that ends this way.
        GameEventBus.getInstance().subscribe("GAME_OVER", data -> stopRendering());

        this.renderTimer = new Timer(RENDER_INTERVAL_MS, e -> {
            GameSnapshot snapshot = lastSnapshot;
            if (snapshot == null) return;
            Img newFrame = composeFrame(snapshot, lastWindowWidth, lastWindowHeight);
            GameEventBus.getInstance().publish("FRAME_READY", newFrame);
        });
        this.renderTimer.start();
    }

    public void stopRendering() {
        renderTimer.stop();
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