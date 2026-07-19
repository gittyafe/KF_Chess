package org.example.view;

import static org.example.view.LayoutConstants.*;

/**
 * Computes where the board and side panels go for a given window size.
 */
public class BoardLayoutCalculator {

    /** All the pixel positions/sizes needed to draw one frame. */
    public record Metrics(
            int windowWidth,
            int windowHeight,
            int boardX,
            int boardY,
            int boardSize,
            int panelWidth,
            int leftPanelX,
            int rightPanelX
    ) {}

    public Metrics calculate(int rawWindowWidth, int rawWindowHeight) {
        int width = Math.max(MIN_WINDOW_WIDTH_PX, rawWindowWidth);
        int height = Math.max(MIN_WINDOW_HEIGHT_PX, rawWindowHeight);

        int boardSize = (int) Math.min(width * BOARD_WIDTH_TO_WINDOW_RATIO, height - VERTICAL_CHROME_PX);
        boardSize = Math.max(MIN_BOARD_SIZE_PX, boardSize);

        int boardY = (height - boardSize) / 2;
        int boardX = (width - boardSize) / 2;

        int panelWidth = Math.min(MAX_PANEL_WIDTH_PX, (int) (width * PANEL_WIDTH_TO_WINDOW_RATIO));

        int leftPanelX = (boardX - panelWidth) / 2;
        int rightPanelX = boardX + boardSize + leftPanelX;

        return new Metrics(width, height, boardX, boardY, boardSize, panelWidth, leftPanelX, rightPanelX);
    }
}
