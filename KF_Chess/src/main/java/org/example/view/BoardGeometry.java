package org.example.view;

import java.awt.Dimension;

/**
 * גיאומטריית הלוח - אחראית על תרגום בין קואורדינטות לוגיות (עמודה/שורה)
 * לקואורדינטות פיקסלים, ולהפך.
 *
 * <p><b>בטיחות תהליכונים (Thread Safety):</b> האובייקט מעודכן מה-thread של
 * לולאת המשחק (בכל פריים, לפי גודל החלון הנוכחי) ונקרא הן מאותו thread והן
 * מ-EDT (בזמן טיפול בלחיצות עכבר, כדי לתרגם פיקסל -> משבצת). כשהיו כאן שני
 * שדות int נפרדים (boardSizePx, cellSize) בלי volatile, יכול היה להיווצר
 * מצב שבו thread אחר קורא את אחד השדות "ישן" ואת השני "חדש" (torn read) -
 * בדיוק בזמן שינוי גודל, מה שמסביר תרגום שגוי של לחיצה לכלי הלא נכון.
 * הפתרון: כל הערכים המחושבים ביחד ארוזים בתמונת מצב (snapshot) בלתי ניתנת
 * לשינוי, שמתפרסמת אטומית דרך שדה volatile יחיד.</p>
 */
public class BoardGeometry {

    /** תמונת מצב אטומית ובלתי ניתנת לשינוי של כל הגדלים המחושבים יחד */
    private record Snapshot(int boardSizePx, int cellSize) {}

    private final int cols;
    private final int rows;
    private final int pieceMarginPx;
    private volatile Snapshot snapshot;

    public BoardGeometry(int boardSizePx, int cols, int rows, int pieceMarginPx) {
        if (cols <= 0 || rows <= 0) {
            throw new IllegalArgumentException("cols/rows must be positive");
        }
        this.cols = cols;
        this.rows = rows;
        this.pieceMarginPx = pieceMarginPx;
        updateSize(boardSizePx);
    }

    /**
     * מעדכן את גודל הלוח בעקבות שינוי גודל חלון. בטוח לקרוא מכל thread -
     * כל הערכים הנגזרים (boardSizePx, cellSize) מתפרסמים יחד, אטומית.
     */
    public void updateSize(int newBoardSizePx) {
        int newCellSize = newBoardSizePx / cols;
        this.snapshot = new Snapshot(newBoardSizePx, newCellSize);
    }

    public int getBoardSizePx() { return snapshot.boardSizePx(); }
    public int getCellSize() { return snapshot.cellSize(); }
    public int getCols() { return cols; }
    public int getRows() { return rows; }

    public Dimension getPieceTargetSize() {
        int side = getCellSize() - (pieceMarginPx * 2);
        return new Dimension(Math.max(1, side), Math.max(1, side));
    }

    public int pixelX(int col) {
        Snapshot s = snapshot; // קריאה יחידה - שני החישובים תמיד עקביים זה עם זה
        return (col * s.boardSizePx()) / cols + pieceMarginPx;
    }

    public int pixelY(int row) {
        Snapshot s = snapshot;
        return (row * s.boardSizePx()) / rows + pieceMarginPx;
    }

    /**
     * ממיר פיקסל X (יחסית לפינת הלוח, כלומר אחרי הפחתת boardX) לאינדקס
     * עמודה לוגי. זו הדרך הבטוחה היחידה לתרגם לחיצת עכבר לעמודה - כי היא
     * קוראת snapshot אחד עקבי במקום לחשב לפי cellSize שנקרא בנפרד.
     * מחזיר -1 אם הפיקסל מחוץ לגבולות הלוח.
     */
    public int columnAt(int pixelXRelativeToBoard) {
        Snapshot s = snapshot;
        if (s.cellSize() <= 0) return -1;
        int col = pixelXRelativeToBoard / s.cellSize();
        return (col >= 0 && col < cols) ? col : -1;
    }

    /** מקביל ל-columnAt, עבור ציר ה-Y. מחזיר -1 אם הפיקסל מחוץ לגבולות. */
    public int rowAt(int pixelYRelativeToBoard) {
        Snapshot s = snapshot;
        if (s.cellSize() <= 0) return -1;
        int row = pixelYRelativeToBoard / s.cellSize();
        return (row >= 0 && row < rows) ? row : -1;
    }
}
