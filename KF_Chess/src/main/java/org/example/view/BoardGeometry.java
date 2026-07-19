package org.example.view;

import java.awt.Dimension;

/**
 * translating between logical board coordinates (row/col) and pixel coordinates on the screen.
 */
public class BoardGeometry {

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
     * Updates the size of the board based on the new window size.
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

    public int columnAt(int pixelXRelativeToBoard) {
        Snapshot s = snapshot;
        if (s.cellSize() <= 0) return -1;
        int col = pixelXRelativeToBoard / s.cellSize();
        return (col >= 0 && col < cols) ? col : -1;
    }

    public int rowAt(int pixelYRelativeToBoard) {
        Snapshot s = snapshot;
        if (s.cellSize() <= 0) return -1;
        int row = pixelYRelativeToBoard / s.cellSize();
        return (row >= 0 && row < rows) ? row : -1;
    }
}
