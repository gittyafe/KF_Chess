package org.example.view;

import org.example.models.State;

/**
 * מחלקה זו מייצגת כלי בעולם הגרפי עם החלקה חלקה (Linear interpolation with ease-out).
 * שינויים: הגנה מפני חלוקה באפס בחישוב ההתקדמות.
 */
public class VisualPiece {
    public double currentX;
    public double currentY;

    public int startX;
    public int startY;
    public int targetX;
    public int targetY;

    public State state;      // המצב הנוכחי שהמנוע שלח

    public long stateStartTime; // מתי הסטייט התחיל
    public long moveStartTime;  // מתי התנועה הפיזית הנוכחית התחילה

    private long currentMoveDurationMs = 300;

    public VisualPiece(int startX, int startY, State state, long startTime) {
        this.currentX = startX;
        this.currentY = startY;
        this.startX = startX;
        this.startY = startY;
        this.targetX = startX;
        this.targetY = startY;
        this.state = state;
        this.stateStartTime = startTime;
        this.moveStartTime = startTime;
    }

    public void setNewTarget(int newTargetX, int newTargetY, long durationMs, long frameTime) {
        this.startX = (int) this.currentX;
        this.startY = (int) this.currentY;
        this.targetX = newTargetX;
        this.targetY = newTargetY;
        // ביטחון כפול: מניעת משך תנועה לא חוקי
        this.currentMoveDurationMs = Math.max(50, durationMs);
        this.moveStartTime = frameTime;
    }

    public void updatePosition(long frameTime) {
        long elapsed = frameTime - moveStartTime;

        if (elapsed >= currentMoveDurationMs) {
            currentX = targetX;
            currentY = targetY;
        } else {
            double progress = (double) elapsed / currentMoveDurationMs;
            // הגבלת הטווח של progress בין 0 ל-1 למניעת עיוותים
            progress = Math.max(0.0, Math.min(1.0, progress));

            // נוסחת ה-Ease-Out (ריכוך לקראת העצירה)
            progress = 1 - Math.pow(1 - progress, 3);

            currentX = startX + (targetX - startX) * progress;
            currentY = startY + (targetY - startY) * progress;
        }
    }
}