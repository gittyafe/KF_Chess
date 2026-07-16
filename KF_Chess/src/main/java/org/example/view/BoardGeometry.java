package org.example.view;

import java.awt.Dimension;

/**
 * מקור אמת יחיד לגיאומטריית הלוח.
 *
 * הבאג המקורי: ImgRenderer טען את תמונת הלוח בגודל הטבעי שלה (ללא resize),
 * בעוד ש-cellSize היה קבוע נפרד שהוזן ידנית ל-constructor. כשהשניים לא
 * תאמו במדויק (board_px / cols != cellSize), הכלים צוירו לפי גריד מתמטי
 * שלא תאם את הגריד הגרפי בפועל בתמונה - ומכאן חוסר היישור.
 *
 * הפתרון: קובעים גודל לוח יעד (boardSizePx), וגוזרים ממנו את cellSize.
 * גם תמונת הלוח (ב-ImgRenderer) וגם תמונות הכלים (ב-PieceImageLoader)
 * משתמשות באותו מופע של המחלקה הזו - כך שאין אפשרות לחוסר סנכרון.
 *
 * כדי להקטין את הלוח: פשוט שנו את boardSizePx בנקודה אחת (למשל ב-Main /
 * מקום ההרכבה של האפליקציה). כל שאר החישובים יתעדכנו אוטומטית.
 */
public class BoardGeometry {

    private final int boardSizePx; // הגודל הרצוי של תמונת הלוח (ריבועי), בפיקסלים
    private final int cols;
    private final int rows;
    private final int cellSize;
    private final int pieceMarginPx; // "ריפוד" פנימי סביב כל כלי בתוך המשבצת שלו

    public BoardGeometry(int boardSizePx, int cols, int rows, int pieceMarginPx) {
        if (cols <= 0 || rows <= 0) {
            throw new IllegalArgumentException("cols/rows must be positive");
        }
        if (boardSizePx % cols != 0 || boardSizePx % rows != 0) {
            // לא זורקים - אבל שווה לדעת: חלוקה לא שלמה תיצור עיגול (rounding)
            // שעלול לגרום לסטייה מצטברת קטנה בין העמודה הראשונה לאחרונה.
            System.out.println("[BoardGeometry] Warning: boardSizePx=" + boardSizePx
                    + " does not divide evenly by cols/rows (" + cols + "x" + rows + ")");
        }
        this.boardSizePx = boardSizePx;
        this.cols = cols;
        this.rows = rows;
        this.pieceMarginPx = pieceMarginPx;
        this.cellSize = boardSizePx / cols;
    }

    public int getBoardSizePx() {
        return boardSizePx;
    }

    public int getCellSize() {
        return cellSize;
    }

    public int getCols() {
        return cols;
    }

    public int getRows() {
        return rows;
    }

    /** גודל היעד שאליו יש לטעון/לשנות (resize) כל תמונת כלי, כך שתתאים בדיוק לתא. */
    public Dimension getPieceTargetSize() {
        int side = cellSize - (pieceMarginPx * 2);
        return new Dimension(side, side);
    }

    /** מיקום ה-X בפיקסלים של כלי בעמודה נתונה (כולל הריפוד הפנימי). */
    public int pixelX(int col) {
        return col * cellSize + pieceMarginPx;
    }

    /** מיקום ה-Y בפיקסלים של כלי בשורה נתונה (כולל הריפוד הפנימי). */
    public int pixelY(int row) {
        return row * cellSize + pieceMarginPx;
    }
}
