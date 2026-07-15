package org.example.view;

import org.example.models.State;

public class VisualPiece {
    public double currentX;
    public double currentY;

    public int startX;
    public int startY;
    public int targetX;
    public int targetY;

    public State lastState;
    public long stateStartTime;

    private long currentMoveDurationMs = 300;

    public VisualPiece(int startX, int startY, long startTime) {
        this.currentX = startX;
        this.currentY = startY;
        this.startX = startX;
        this.startY = startY;
        this.targetX = startX;
        this.targetY = startY;
        this.stateStartTime = startTime;
    }

    public void setNewTarget(int newTargetX, int newTargetY, long durationMs, long startTime) {
        this.startX = (int) this.currentX;
        this.startY = (int) this.currentY;
        this.targetX = newTargetX;
        this.targetY = newTargetY;
        this.currentMoveDurationMs = durationMs;
        this.stateStartTime = startTime;
    }

    public void updatePosition(long currentTime) {
        long elapsed = currentTime - stateStartTime;

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