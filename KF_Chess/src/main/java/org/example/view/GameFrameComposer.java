package org.example.view;

import java.awt.Color;
import java.util.List;

import org.example.Img;
import org.example.engines.GameSnapshot;

/**
 * מרכיבה את הפריים הסופי: לוח + טבלאות היסטוריית מהלכים משני הצדדים.
 *
 * שינויים בריפקטור: הועברו כל ה"מספרי קסם" לקבועים בעלי שם, כדי שיהיה
 * ברור מה כל ערך מייצג ואיפה לשנות אותו (למשל אם משנים את גודל הלוח
 * דרך BoardGeometry, יש לוודא שה-masterFrame עדיין גדול מספיק).
 */
public class GameFrameComposer {

    // מיקום הלוח בתוך המסגרת הכוללת
    private static final int BOARD_X = 250;
    private static final int BOARD_Y = 50;

    // פריסת טבלאות ההיסטוריה
    private static final int TABLE_WIDTH = 200;
    private static final int TABLE_HEIGHT = 500;
    private static final int BLACK_TABLE_X = 20;
    private static final int WHITE_TABLE_X = 1150;
    private static final int TABLE_Y = 50;
    private static final Color TABLE_BG_COLOR = new Color(240, 240, 240);

    private static final int TITLE_OFFSET_X = 70;
    private static final int TITLE_OFFSET_Y = 25;
    private static final int HEADER_LINE_OFFSET_Y = 40;
    private static final int FIRST_ROW_OFFSET_Y = 70;
    private static final int ROW_HEIGHT = 30;
    private static final int TIME_COLUMN_OFFSET_X = 10;
    private static final int MOVE_COLUMN_OFFSET_X = 120;

    private final ImgRenderer boardRenderer;
    private final GameHistoryManager historyManager;

    public GameFrameComposer(ImgRenderer boardRenderer, GameHistoryManager historyManager) {
        this.boardRenderer = boardRenderer;
        this.historyManager = historyManager;
    }

    // חשיפה מבוקרת של מיקום הלוח, נדרש למשל ע"י GameWindow לחישוב קליקים
    public static int getBoardX() {
        return BOARD_X;
    }

    public static int getBoardY() {
        return BOARD_Y;
    }

    public Img composeFrame(GameSnapshot snapshot) {
        Img boardImg = boardRenderer.drawGame(snapshot);

        Img masterFrame = new Img().createEmpty(DisplayConstants.MASTER_WIDTH, DisplayConstants.MASTER_HEIGHT, true);
        boardImg.drawOn(masterFrame, BOARD_X, BOARD_Y);

        drawHistoryTable(masterFrame, historyManager.blackMoves, BLACK_TABLE_X, TABLE_Y, "Black");
        drawHistoryTable(masterFrame, historyManager.whiteMoves, WHITE_TABLE_X, TABLE_Y, "White");

        return masterFrame;
    }

    private void drawHistoryTable(Img frame, List<MoveEntry> moves, int x, int y, String title) {
        if (x < 0 || x + TABLE_WIDTH > frame.get().getWidth()) {
            return;
        }

        frame.drawRect(x, y, TABLE_WIDTH, TABLE_HEIGHT, TABLE_BG_COLOR);
        frame.putText(title, x + TITLE_OFFSET_X, y + TITLE_OFFSET_Y, 2.0f, Color.BLACK, 2);
        frame.drawLine(x, y + HEADER_LINE_OFFSET_Y, x + TABLE_WIDTH, y + HEADER_LINE_OFFSET_Y, Color.GRAY);

        int rowY = y + FIRST_ROW_OFFSET_Y;
        for (MoveEntry move : moves) {
            frame.putText(move.getTimeString(), x + TIME_COLUMN_OFFSET_X, rowY, 1.7f, Color.DARK_GRAY, 0);
            frame.putText(move.getMoveNotation(), x + MOVE_COLUMN_OFFSET_X, rowY, 1.7f, Color.BLACK, 0);
            rowY += ROW_HEIGHT;
        }
    }
}
