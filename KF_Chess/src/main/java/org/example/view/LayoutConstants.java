package org.example.view;

import java.awt.Color;

/**
 * Named values for the game frame's layout and palette.
 */
public final class LayoutConstants {
    private LayoutConstants() {}

    // --- Window / board sizing ---
    public static final int MIN_WINDOW_WIDTH_PX = 400;
    public static final int MIN_WINDOW_HEIGHT_PX = 300;
    /** Board width as a fraction of the total window width. */
    public static final double BOARD_WIDTH_TO_WINDOW_RATIO = 0.55;
    /** Vertical space reserved outside the board (top/bottom margins) when sizing by height. */
    public static final int VERTICAL_CHROME_PX = 160;
    public static final int MIN_BOARD_SIZE_PX = 200;

    // --- Side panels ---
    public static final int MAX_PANEL_WIDTH_PX = 220;
    public static final double PANEL_WIDTH_TO_WINDOW_RATIO = 0.18;
    public static final int PANEL_NAME_LABEL_OFFSET_Y = 30;
    public static final int SCORE_BOX_HEIGHT_PX = 50;
    public static final int SCORE_TEXT_OFFSET_Y = 32;
    public static final int TABLE_TOP_MARGIN_PX = 60;
    public static final int TABLE_HEADER_HEIGHT_PX = 30;
    public static final int TABLE_ROW_HEIGHT_PX = 28;
    public static final int TABLE_HEADER_TEXT_OFFSET_Y = 20;
    public static final int TABLE_CELL_LEFT_PADDING_PX = 15;
    public static final int TABLE_CELL_TEXT_OFFSET_Y = 18;

    // --- Coordinate labels ---
    public static final int COLUMN_LABEL_OFFSET_ABOVE_PX = 20;
    public static final int COLUMN_LABEL_OFFSET_BELOW_PX = 25;
    public static final int COLUMN_LABEL_TEXT_NUDGE_X = -5;
    public static final int ROW_LABEL_LEFT_OFFSET_PX = 25;
    public static final int ROW_LABEL_RIGHT_OFFSET_PX = 12;
    public static final int ROW_LABEL_TEXT_NUDGE_Y = 6;

    // --- Frame chrome ---
    public static final int FRAME_BORDER_THICKNESS_PX = 4;

    // --- Colors ---
    public static final Color BACKGROUND_COLOR = new Color(225, 227, 230);
    public static final Color FRAME_BORDER_COLOR = new Color(70, 110, 150);
    public static final Color PANEL_BORDER_COLOR = new Color(160, 170, 185);
    public static final Color PANEL_TABLE_HEADER_BG = new Color(210, 215, 225);
    public static final Color PANEL_TABLE_ALT_ROW_BG = new Color(245, 247, 250);
    public static final Color SCORE_TEXT_COLOR = new Color(50, 70, 95);

    // --- Text ---
    public static final float PANEL_TITLE_FONT_SIZE = 1.2f;
    public static final float TABLE_HEADER_FONT_SIZE = 0.7f;
    public static final float TABLE_CELL_FONT_SIZE = 1.2f;
    public static final float COORDINATE_LABEL_FONT_SIZE = 1.1f;
    /** Empirical average glyph width (px) per unit of font size, used to center short text labels. */
    public static final double AVG_GLYPH_WIDTH_PER_FONT_UNIT = 7.5;
}
