package org.example.view;

import org.example.Img;
import org.example.engines.GameHistoryManager;
import org.example.engines.GameSnapshot;

/**
 * Composes one full window frame: background, board, coordinate labels and
 * both player panels.
 *
 * <p>Thin orchestrator: asks {@link BoardLayoutCalculator} for sizing, then
 * hands off to {@link ImgRenderer} (the board itself) and {@link FrameRenderer}
 * (everything around it).</p>
 */
public class GameFrameComposer {
    private final ImgRenderer boardRenderer;
    private final GameHistoryManager historyManager;
    private final BoardGeometry geometry;
    private final ScoreManager scoreManager;

    private final BoardLayoutCalculator layoutCalculator = new BoardLayoutCalculator();
    private final FrameRenderer frameRenderer;

    private volatile BoardLayoutCalculator.Metrics lastLayout;

    public GameFrameComposer(ImgRenderer boardRenderer, GameHistoryManager historyManager,
                              BoardGeometry geometry, ScoreManager scoreManager) {
        this.boardRenderer = boardRenderer;
        this.historyManager = historyManager;
        this.geometry = geometry;
        this.scoreManager = scoreManager;
        this.frameRenderer = new FrameRenderer(geometry);
    }

    public int getBoardX() { return lastLayout != null ? lastLayout.boardX() : 0; }
    public int getBoardY() { return lastLayout != null ? lastLayout.boardY() : 0; }

    public Img composeFrame(GameSnapshot snapshot, int currentWindowWidth, int currentWindowHeight) {
        BoardLayoutCalculator.Metrics layout = layoutCalculator.calculate(currentWindowWidth, currentWindowHeight);
        geometry.updateSize(layout.boardSize());
        this.lastLayout = layout;

        Img masterFrame = new Img().createEmpty(layout.windowWidth(), layout.windowHeight(), true);

        frameRenderer.drawBackground(masterFrame, layout.windowWidth(), layout.windowHeight());

        Img boardImg = boardRenderer.drawGame(snapshot);
        boardImg.drawOn(masterFrame, layout.boardX(), layout.boardY());

        frameRenderer.drawColumnLabels(masterFrame, layout, true);
        frameRenderer.drawColumnLabels(masterFrame, layout, false);
        frameRenderer.drawRowLabels(masterFrame, layout);

        frameRenderer.drawPlayerPanel(masterFrame, "BLACK", scoreManager.getBlackScore(), historyManager.getBlackMoves(),
                layout.leftPanelX(), layout.boardY(), layout.boardSize(), layout.panelWidth());
        frameRenderer.drawPlayerPanel(masterFrame, "WHITE", scoreManager.getWhiteScore(), historyManager.getWhiteMoves(),
                layout.rightPanelX(), layout.boardY(), layout.boardSize(), layout.panelWidth());

        return masterFrame;
    }
}
