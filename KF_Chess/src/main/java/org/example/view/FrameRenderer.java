package org.example.view;

import java.awt.Color;
import java.util.List;

import org.example.Img;
import org.example.engines.MoveEntry;

import static org.example.view.LayoutConstants.*;

/**
 * Draws everything around the board that isn't the board itself: page
 * background, outer border, coordinate labels, and both player panels
 * (name / score / move history).
 *
 * <p>This used to be four separate classes ({@code FrameBackgroundRenderer},
 * {@code BoardLabelRenderer}, {@code PlayerPanelRenderer}, {@code ImgDrawUtils}).
 * They didn't share any state and each only had one or two small methods -
 * splitting "static frame furniture" into four files bought isolation the
 * problem didn't need and cost more file-hopping than it saved. One class
 * with clearly named private methods gives the same readability with less
 * ceremony.</p>
 */
public class FrameRenderer {
    private final BoardGeometry geometry;

    public FrameRenderer(BoardGeometry geometry) {
        this.geometry = geometry;
    }

    public void drawBackground(Img frame, int width, int height) {
        frame.fillRect(0, 0, width, height, BACKGROUND_COLOR);
        drawOutline(frame, 0, 0, width, height, FRAME_BORDER_COLOR, FRAME_BORDER_THICKNESS_PX);
    }

    public void drawColumnLabels(Img frame, BoardLayoutCalculator.Metrics layout, boolean above) {
        int y = above
                ? layout.boardY() - COLUMN_LABEL_OFFSET_ABOVE_PX
                : layout.boardY() + layout.boardSize() + COLUMN_LABEL_OFFSET_BELOW_PX;
        int cellSize = geometry.getCellSize();
        for (int col = 0; col < geometry.getCols(); col++) {
            char letter = (char) ('a' + col);
            int cellCenterX = layout.boardX() + col * cellSize + cellSize / 2;
            frame.putText(String.valueOf(letter), cellCenterX + COLUMN_LABEL_TEXT_NUDGE_X, y,
                    COORDINATE_LABEL_FONT_SIZE, Color.BLACK, 1);
        }
    }

    public void drawRowLabels(Img frame, BoardLayoutCalculator.Metrics layout) {
        int cellSize = geometry.getCellSize();
        int leftX = layout.boardX() - ROW_LABEL_LEFT_OFFSET_PX;
        int rightX = layout.boardX() + layout.boardSize() + ROW_LABEL_RIGHT_OFFSET_PX;
        for (int row = 0; row < geometry.getRows(); row++) {
            int cellCenterY = layout.boardY() + row * cellSize + cellSize / 2 + ROW_LABEL_TEXT_NUDGE_Y;
            String text = String.valueOf(row + 1);
            frame.putText(text, leftX, cellCenterY, COORDINATE_LABEL_FONT_SIZE, Color.BLACK, 1);
            frame.putText(text, rightX, cellCenterY, COORDINATE_LABEL_FONT_SIZE, Color.BLACK, 1);
        }
    }

    public void drawPlayerPanel(Img frame, String playerName, int score, List<MoveEntry> moves,
                                 int x, int boardY, int boardSize, int panelWidth) {
        int centerX = x + (panelWidth / 2);

        drawCenteredText(frame, "Player: " + playerName, centerX, boardY - PANEL_NAME_LABEL_OFFSET_Y,
                PANEL_TITLE_FONT_SIZE, Color.BLACK);

        drawScoreBox(frame, score, x, boardY, panelWidth, centerX);
        drawMoveTable(frame, moves, x, boardY, boardSize, panelWidth);
    }

    private void drawScoreBox(Img frame, int score, int x, int boardY, int panelWidth, int centerX) {
        frame.fillRect(x, boardY, panelWidth, SCORE_BOX_HEIGHT_PX, Color.WHITE);
        drawOutline(frame, x, boardY, panelWidth, SCORE_BOX_HEIGHT_PX, PANEL_BORDER_COLOR, 1);
        drawCenteredText(frame, "SCORE: " + score, centerX, boardY + SCORE_TEXT_OFFSET_Y,
                PANEL_TITLE_FONT_SIZE, SCORE_TEXT_COLOR);
    }

    private void drawMoveTable(Img frame, List<MoveEntry> moves, int x, int boardY, int boardSize, int panelWidth) {
        int tableY = boardY + TABLE_TOP_MARGIN_PX;
        int tableHeight = boardSize - TABLE_TOP_MARGIN_PX;

        frame.fillRect(x, tableY, panelWidth, tableHeight, Color.WHITE);
        drawOutline(frame, x, tableY, panelWidth, tableHeight, PANEL_BORDER_COLOR, 1);

        frame.fillRect(x, tableY, panelWidth, TABLE_HEADER_HEIGHT_PX, PANEL_TABLE_HEADER_BG);
        frame.putText("Time", x + TABLE_CELL_LEFT_PADDING_PX, tableY + TABLE_HEADER_TEXT_OFFSET_Y,
                TABLE_HEADER_FONT_SIZE, Color.BLACK, 2);
        frame.putText("Move", x + (panelWidth / 2) + TABLE_CELL_LEFT_PADDING_PX, tableY + TABLE_HEADER_TEXT_OFFSET_Y,
                TABLE_HEADER_FONT_SIZE, Color.BLACK, 2);

        int rowY = tableY + TABLE_HEADER_HEIGHT_PX;
        int maxRows = (tableHeight - TABLE_HEADER_HEIGHT_PX) / TABLE_ROW_HEIGHT_PX;

        for (int i = 0; i < Math.min(moves.size(), maxRows); i++) {
            MoveEntry move = moves.get(i);
            if (i % 2 == 1) {
                frame.fillRect(x, rowY, panelWidth, TABLE_ROW_HEIGHT_PX, PANEL_TABLE_ALT_ROW_BG);
            }
            frame.putText(move.timeString(), x + TABLE_CELL_LEFT_PADDING_PX, rowY + TABLE_CELL_TEXT_OFFSET_Y,
                    TABLE_CELL_FONT_SIZE, Color.DARK_GRAY, 0);
            frame.putText(move.moveNotation(), x + (panelWidth / 2) + TABLE_CELL_LEFT_PADDING_PX,
                    rowY + TABLE_CELL_TEXT_OFFSET_Y, TABLE_CELL_FONT_SIZE, Color.BLACK, 0);
            rowY += TABLE_ROW_HEIGHT_PX;
        }
    }

    private void drawCenteredText(Img frame, String text, int centerX, int y, float fontSize, Color color) {
        int textWidth = (int) (text.length() * fontSize * AVG_GLYPH_WIDTH_PER_FONT_UNIT);
        frame.putText(text, centerX - textWidth / 2, y, fontSize, color, 2);
    }

    /** Draws a rectangular border as four solid bars (Img.drawRect only ever strokes, never fills - see Img.fillRect). */
    private void drawOutline(Img frame, int x, int y, int w, int h, Color color, int thickness) {
        frame.fillRect(x, y, w, thickness, color);
        frame.fillRect(x, y + h - thickness, w, thickness, color);
        frame.fillRect(x, y, thickness, h, color);
        frame.fillRect(x + w - thickness, y, thickness, h, color);
    }
}
