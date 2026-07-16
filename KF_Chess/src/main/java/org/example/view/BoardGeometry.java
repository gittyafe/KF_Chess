package org.example.view;

import java.awt.Dimension;

/**
 * מחלקה מתוקנת המבטלת את בעיית הסטייה של גיאומטריית הלוח.
 * במקום להשתמש בחילוק שלם שמחסיר שאריות פיקסלים ומעוות את הלוח,
 * אנו משתמשים בכפל מקדים לפני חילוק כדי למרכז את הכלים באופן מושלם.
 */
public class BoardGeometry {

    private final int boardSizePx;
    private final int cols;
    private final int rows;
    private final int cellSize;
    private final int pieceMarginPx;

    public BoardGeometry(int boardSizePx, int cols, int rows, int pieceMarginPx) {
        if (cols <= 0 || rows <= 0) {
            throw new IllegalArgumentException("cols/rows must be positive");
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

    public Dimension getPieceTargetSize() {
        int side = cellSize - (pieceMarginPx * 2);
        return new Dimension(side, side);
    }

    /**
     * תיקון לוגיקה למניעת סטיית פיקסלים (Floating Pixel Drift):
     * החישוב (col * boardSizePx) / cols מבטיח ששארית הפיקסלים
     * תחולק באופן שווה לגמרי לרוחב כל העמודות, מה שמיישר את הכלים בצורה מושלמת.
     */
    public int pixelX(int col) {
        return (col * boardSizePx) / cols + pieceMarginPx;
    }

    public int pixelY(int row) {
        return (row * boardSizePx) / rows + pieceMarginPx;
    }
}