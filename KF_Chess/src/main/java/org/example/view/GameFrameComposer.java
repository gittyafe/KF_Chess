package org.example.view;

import java.awt.Color;
import java.util.List;

import org.example.Img;
import org.example.engines.GameSnapshot;

/**
 * מרכיבה את הפריים הסופי בהשראת התצוגה הרפרנס:
 * שם שחקן, כפתור זיהוי, וניקוד אישי מתחת לטבלת ההיסטוריה שלו.
 * הלוח ממורכז ומסביבו קואורדינטות בצורה סימטרית ונקייה.
 */
public class GameFrameComposer {

    // גודל החלון הכללי (גובה נמוך יותר ומראה קומפקטי לרוחב)
    private static final int MASTER_WIDTH = 1400;
    private static final int MASTER_HEIGHT = 780;

    // מיקום הלוח ממורכז (הלוח בגודל 650, נשאר לנו 750 פיקסלים פנויים בצדדים)
    private static final int BOARD_X = 375;
    private static final int BOARD_Y = 65;

    // רקע כללי ומסגרת חיצונית
    private static final Color WINDOW_BG_COLOR = new Color(225, 227, 230);
    private static final Color WINDOW_BORDER_COLOR = new Color(70, 110, 150);
    private static final int WINDOW_BORDER_THICKNESS = 4;

    // גופנים וגדלים
    private static final float NAME_FONT_SIZE = 1.3f;
    private static final float SCORE_FONT_SIZE = 1.3f;

    // הגדרות כפתורים (Black / White)
    private static final int BUTTON_WIDTH = 110;
    private static final int BUTTON_HEIGHT = 32;
    private static final Color BUTTON_BG_COLOR = Color.WHITE;
    private static final Color BUTTON_BORDER_COLOR = new Color(120, 120, 120);

    // קואורדינטות לוח
    private static final int COLUMN_LABEL_GAP = 20; // מרחק מעל ומתחת ללוח
    private static final int ROW_LABEL_GAP = 22;     // מרחק משמאל ומימין ללוח
    private static final float COORD_FONT_SIZE = 1.1f;
    private static final char[] COLUMN_LETTERS = {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'};

    // פריסת טבלאות ההיסטוריה
    private static final int TABLE_WIDTH = 220;
    private static final int TABLE_HEIGHT = 420;
    private static final int BLACK_PANEL_X = 60;    // פאנל שמאלי (Black)
    private static final int WHITE_PANEL_X = 1120;  // פאנל ימני (White)
    private static final int TABLE_Y = 170;

    private static final Color TABLE_BG_COLOR = Color.WHITE;
    private static final Color TABLE_HEADER_BG_COLOR = new Color(210, 215, 225);
    private static final Color TABLE_ROW_ALT_COLOR = new Color(245, 247, 250);
    private static final Color TABLE_BORDER_COLOR = new Color(160, 170, 185);

    private static final int HEADER_HEIGHT = 35;
    private static final float TABLE_HEADER_FONT_SIZE = 0.7f;
    private static final int ROW_HEIGHT = 30;
    private static final int TIME_COLUMN_OFFSET_X = 15;
    private static final int MOVE_COLUMN_OFFSET_X = 130;

    // קופסת ניקוד (Score Box) מתחת לטבלת היסטוריה
    private static final int SCORE_BOX_HEIGHT = 65;

    private final ImgRenderer boardRenderer;
    private final GameHistoryManager historyManager;
    private final BoardGeometry geometry;
    private final PlayerInfo topPlayer;    // Black
    private final PlayerInfo bottomPlayer; // White

    public GameFrameComposer(ImgRenderer boardRenderer, GameHistoryManager historyManager,
                             BoardGeometry geometry, PlayerInfo topPlayer, PlayerInfo bottomPlayer) {
        this.boardRenderer = boardRenderer;
        this.historyManager = historyManager;
        this.geometry = geometry;
        this.topPlayer = topPlayer;
        this.bottomPlayer = bottomPlayer;
    }

    public static int getMasterWidth() {
        return MASTER_WIDTH;
    }

    public static int getMasterHeight() {
        return MASTER_HEIGHT;
    }

    public static int getBoardX() {
        return BOARD_X;
    }

    public static int getBoardY() {
        return BOARD_Y;
    }

    public Img composeFrame(GameSnapshot snapshot) {
        Img boardImg = boardRenderer.drawGame(snapshot);

        Img masterFrame = new Img().createEmpty(MASTER_WIDTH, MASTER_HEIGHT, true);
        drawWindowChrome(masterFrame);

        int boardSize = geometry.getBoardSizePx();

        // 1. ציור קואורדינטות (עמודות ושורות)
        int colLabelTopY = BOARD_Y - COLUMN_LABEL_GAP;
        drawColumnLabels(masterFrame, colLabelTopY);

        int colLabelBottomY = BOARD_Y + boardSize + COLUMN_LABEL_GAP + 10;
        drawColumnLabels(masterFrame, colLabelBottomY);

        boardImg.drawOn(masterFrame, BOARD_X, BOARD_Y);
        drawRowLabels(masterFrame, boardSize);

        // 2. פאנל שמאלי - שחקן שחור (Black)
        drawPlayerPanelHeader(masterFrame, topPlayer.name, "Black", BLACK_PANEL_X);
        drawHistoryTable(masterFrame, historyManager.blackMoves, BLACK_PANEL_X, TABLE_Y);
        drawPlayerScoreBox(masterFrame, topPlayer.score, BLACK_PANEL_X, TABLE_Y + TABLE_HEIGHT + 15);

        // 3. פאנל ימני - שחקן לבן (White)
        drawPlayerPanelHeader(masterFrame, bottomPlayer.name, "White", WHITE_PANEL_X);
        drawHistoryTable(masterFrame, historyManager.whiteMoves, WHITE_PANEL_X, TABLE_Y);
        drawPlayerScoreBox(masterFrame, bottomPlayer.score, WHITE_PANEL_X, TABLE_Y + TABLE_HEIGHT + 15);

        return masterFrame;
    }

    // ---------- רקע ומסגרת ----------

    private void drawWindowChrome(Img frame) {
        frame.drawRect(0, 0, MASTER_WIDTH, MASTER_HEIGHT, WINDOW_BG_COLOR);
        drawOutline(frame, 0, 0, MASTER_WIDTH, MASTER_HEIGHT,
                WINDOW_BORDER_COLOR, WINDOW_BORDER_THICKNESS);
    }

    /** מצייר מסגרת (מלבן חלול) בעובי נתון ע"י ציור 4 פסים דקים. */
    private void drawOutline(Img frame, int x, int y, int w, int h, Color color, int thickness) {
        frame.drawRect(x, y, w, thickness, color);                       // עליון
        frame.drawRect(x, y + h - thickness, w, thickness, color);       // תחתון
        frame.drawRect(x, y, thickness, h, color);                       // שמאלי
        frame.drawRect(x + w - thickness, y, thickness, h, color);       // ימני
    }

    // ---------- פאנל שחקן (כותרות, כפתורים, ניקוד) ----------

    /**
     * מצייר את שם השחקן ואת כפתור הצבע מעליו באופן סימטרי
     */
    private void drawPlayerPanelHeader(Img frame, String name, String colorLabel, int panelX) {
        int centerX = panelX + (TABLE_WIDTH / 2);

        // שם השחקן
        drawCenteredText(frame, "Player: " + name, centerX, 80, NAME_FONT_SIZE, Color.BLACK);

        // כפתור תג הצבע (Black/White)
        int btnX = panelX + (TABLE_WIDTH - BUTTON_WIDTH) / 2;
        drawButton(frame, colorLabel, btnX, 110);
    }

    /**
     * מצייר קופסת ניקוד מעוצבת באופן סימטרי מתחת לטבלה של כל שחקן
     */
    private void drawPlayerScoreBox(Img frame, int score, int x, int y) {
        // רקע לבן וקו מתאר של קופסת הניקוד
        drawOutlinedRect(frame, x, y, TABLE_WIDTH, SCORE_BOX_HEIGHT, Color.WHITE, TABLE_BORDER_COLOR, 1);

        // הניקוד עצמו ממורכז בתוך הקופסה
        int centerX = x + (TABLE_WIDTH / 2);
        int textY = y + (SCORE_BOX_HEIGHT / 2) + 8;
        drawCenteredText(frame, "SCORE: " + score, centerX, textY, SCORE_FONT_SIZE, new Color(50, 70, 95));
    }

    private void drawCenteredText(Img frame, String text, int centerX, int y, float fontSize, Color color) {
        int approxCharWidth = (int) (fontSize * 7.5);
        int textWidth = text.length() * approxCharWidth;
        int x = centerX - textWidth / 2;
        frame.putText(text, x, y, fontSize, color, 2);
    }

    private void drawButton(Img frame, String label, int x, int y) {
        drawOutlinedRect(frame, x, y, BUTTON_WIDTH, BUTTON_HEIGHT, BUTTON_BG_COLOR, BUTTON_BORDER_COLOR, 1);
        int approxCharWidth = 9;
        int textWidth = label.length() * approxCharWidth;
        int textX = x + (BUTTON_WIDTH - textWidth) / 2;
        int textY = y + BUTTON_HEIGHT / 2 + 6;
        frame.putText(label, textX, textY, 1.0f, Color.BLACK, 1);
    }

    private void drawOutlinedRect(Img frame, int x, int y, int w, int h, Color fill, Color border, int thickness) {
        frame.drawRect(x, y, w, h, fill);
        drawOutline(frame, x, y, w, h, border, thickness);
    }

    // ---------- קואורדינטות לוח ----------

    private void drawColumnLabels(Img frame, int y) {
        int cellSize = geometry.getCellSize();
        for (int col = 0; col < COLUMN_LETTERS.length; col++) {
            String letter = String.valueOf(COLUMN_LETTERS[col]);
            int cellCenterX = BOARD_X + col * cellSize + cellSize / 2;
            int x = cellCenterX - 5;
            frame.putText(letter, x, y, COORD_FONT_SIZE, Color.BLACK, 1);
        }
    }

    private void drawRowLabels(Img frame, int boardSize) {
        int cellSize = geometry.getCellSize();
        int leftX = BOARD_X - ROW_LABEL_GAP;
        int rightX = BOARD_X + boardSize + (ROW_LABEL_GAP - 15);

        for (int row = 0; row < geometry.getRows(); row++) {
            int label = geometry.getRows() - row;
            String text = String.valueOf(label);
            int cellCenterY = BOARD_Y + row * cellSize + cellSize / 2 + 6;

            frame.putText(text, leftX, cellCenterY, COORD_FONT_SIZE, Color.BLACK, 1);
            frame.putText(text, rightX, cellCenterY, COORD_FONT_SIZE, Color.BLACK, 1);
        }
    }

    // ---------- טבלאות היסטוריה ----------

    private void drawHistoryTable(Img frame, List<MoveEntry> moves, int x, int y) {
        if (x < 0 || x + TABLE_WIDTH > frame.get().getWidth()) {
            return;
        }

        // רקע הטבלה + מסגרת
        frame.drawRect(x, y, TABLE_WIDTH, TABLE_HEIGHT, TABLE_BG_COLOR);
        drawOutline(frame, x, y, TABLE_WIDTH, TABLE_HEIGHT, TABLE_BORDER_COLOR, 1);

        // שורת כותרת (Time / Move)
        frame.drawRect(x, y, TABLE_WIDTH, HEADER_HEIGHT, TABLE_HEADER_BG_COLOR);
        frame.putText("Time", x + TIME_COLUMN_OFFSET_X, y + 23, TABLE_HEADER_FONT_SIZE, Color.BLACK, 2);
        frame.putText("Move", x + MOVE_COLUMN_OFFSET_X, y + 23, TABLE_HEADER_FONT_SIZE, Color.BLACK, 2);
        frame.drawLine(x, y + HEADER_HEIGHT, x + TABLE_WIDTH, y + HEADER_HEIGHT, TABLE_BORDER_COLOR);

        int rowY = y + HEADER_HEIGHT;
        int rowIndex = 0;

        // הגבלת כמות השורות הנכנסות פיזית בטבלה כדי למנוע גלישה
        int maxRows = (TABLE_HEIGHT - HEADER_HEIGHT) / ROW_HEIGHT;

        for (MoveEntry move : moves) {
            if (rowIndex >= maxRows) {
                break;
            }
            int rowTop = rowY;
            if (rowIndex % 2 == 1) {
                frame.drawRect(x, rowTop, TABLE_WIDTH, ROW_HEIGHT, TABLE_ROW_ALT_COLOR);
            }
            int textY = rowTop + 20;
            frame.putText(move.getTimeString(), x + TIME_COLUMN_OFFSET_X, textY, 1.4f, Color.DARK_GRAY, 0);
            frame.putText(move.getMoveNotation(), x + MOVE_COLUMN_OFFSET_X, textY, 1.4f, Color.BLACK, 0);

            rowY += ROW_HEIGHT;
            rowIndex++;
        }
    }
}