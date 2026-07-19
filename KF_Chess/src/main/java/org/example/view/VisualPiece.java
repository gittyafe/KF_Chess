package org.example.view;

import org.example.models.State;

public class VisualPiece {
    public State state;
    public long stateStartTime;

    // זמני האנימציה
    public long startTime;
    public long duration;

    // קואורדינטות לוגיות (לוח משחק - שורות ועמודות)
    public double currentCol;
    public double currentRow;

    public int startCol, startRow;
    public int targetCol, targetRow;

    public VisualPiece(int col, int row, State state, long frameTime) {
        this.state = state;
        this.stateStartTime = frameTime;
        this.startTime = frameTime;
        this.duration = 0;

        this.startCol = col;
        this.startRow = row;
        this.targetCol = col;
        this.targetRow = row;

        this.currentCol = col;
        this.currentRow = row;
    }

    public void setNewTarget(int fromCol, int fromRow, int toCol, int toRow, long duration, long frameTime) {
        this.startTime = frameTime;
        this.duration = duration;

        this.startCol = fromCol;
        this.startRow = fromRow;
        this.targetCol = toCol;
        this.targetRow = toRow;
    }

    public void updatePosition(long frameTime) {
        if (duration <= 0) {
            currentCol = targetCol;
            currentRow = targetRow;
            return;
        }

        // חישוב אחוז התקדמות טהור בזמן
        double progress = (double) (frameTime - startTime) / duration;
        progress = Math.max(0.0, Math.min(1.0, progress));

        // אינטרפולציה ליניארית (אלגוריתם Lerp) על בסיס משבצות לוגיות
        currentCol = startCol + (targetCol - startCol) * progress;
        currentRow = startRow + (targetRow - startRow) * progress;
    }
}