package org.example.view;

import org.example.models.State;

public class VisualPiece {
    public double currentX;
    public double currentY;

    public int startX;
    public int startY;
    public int targetX;
    public int targetY;

    public State state;      // מה שהמנוע שלח

    // הפרדה ברורה בין זמנים:
    public long stateStartTime; // מתי הסטייט (האנימציה) התחיל
    public long moveStartTime;  // מתי התנועה הפיזית הנוכחית (ההחלקה) התחילה

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

    // כשמגדירים יעד חדש, מעדכנים רק את זמן התנועה הפיזית!
    public void setNewTarget(int newTargetX, int newTargetY, long durationMs, long frameTime) {
        this.startX = (int) this.currentX;
        this.startY = (int) this.currentY;
        this.targetX = newTargetX;
        this.targetY = newTargetY;
        this.currentMoveDurationMs = durationMs;
        this.moveStartTime = frameTime; // עדכון זמן תחילת ההחלקה בלבד!
    }



    public void updatePosition(long frameTime) {
        long elapsed = frameTime - moveStartTime; // חישוב לפי זמן התנועה הפיזית

        if (elapsed >= currentMoveDurationMs) {
            currentX = targetX;
            currentY = targetY;

        } else {
            double progress = (double) elapsed / currentMoveDurationMs;
            progress = 1 - Math.pow(1 - progress, 3); // Ease-Out

            currentX = startX + (targetX - startX) * progress;
            currentY = startY + (targetY - startY) * progress;
        }
    }

}