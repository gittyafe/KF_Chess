package org.example.view;

import org.example.models.State;

public class VisualPiece {
    public double currentX;
    public double currentY;

    public int startX;
    public int startY;
    public int targetX;
    public int targetY;

    public State engineState;      // מה שהמנוע שלח
    public State animationState;   // מה שמוצג כרגע
    public State previousEngineState;  // הstateהקודם מהמנוע - לטרנזיציות
    
    // הפרדה ברורה בין זמנים:
    public long stateStartTime; // מתי הסטייט (האנימציה) התחיל
    public long moveStartTime;  // מתי התנועה הפיזית הנוכחית (ההחלקה) התחילה

    private long currentMoveDurationMs = 300;
    private static final long REST_DURATION = 500; // 500ms ל-rest states

    public VisualPiece(int startX, int startY, State engineState, State animationState, long startTime) {
        this.currentX = startX;
        this.currentY = startY;
        this.startX = startX;
        this.startY = startY;
        this.targetX = startX;
        this.targetY = startY;
        this.engineState = engineState;
        this.previousEngineState = engineState;
        this.animationState = animationState;
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

    // עדכון engine state עם state machine לטרנזיציות
    public void updateEngineState(State newEngineState, long frameTime) {
        if (newEngineState == engineState) {
            return; // אין שינוי
        }

        previousEngineState = engineState;
        engineState = newEngineState;

        // Handle transitions: MOVING/JUMPING -> REST states
        if (newEngineState == State.IDLE && previousEngineState != State.IDLE) {
            if (previousEngineState == State.MOVING) {
                animationState = State.LONG_REST;
                stateStartTime = frameTime;
            } else if (previousEngineState == State.JUMPING) {
                animationState = State.SHORT_REST;
                stateStartTime = frameTime;
            }
        } else {
            // ישיר: סנכרון animationState עם engineState
            animationState = newEngineState;
            stateStartTime = frameTime;
        }
    }

    // בדוק אם צריך לעבור מREST לIDLE
    public void updateRestTransition(long frameTime) {
        if (animationState == State.LONG_REST || animationState == State.SHORT_REST) {
            long elapsed = frameTime - stateStartTime;
            if (elapsed >= REST_DURATION) {
                animationState = State.IDLE;
                stateStartTime = frameTime;
            }
        }
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

    public long getCurrentMoveDurationMs() {
        return currentMoveDurationMs;
    }
}