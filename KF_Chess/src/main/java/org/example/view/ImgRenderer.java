package org.example.view;

import java.awt.Color;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.example.Img;
import org.example.engines.GameSnapshot;
import org.example.engines.PieceSnapshot;
import org.example.models.State;
import org.example.view.AnimationConfig;

public class ImgRenderer {
    private final String boardPath;
    private final int cellSize;
    private final PieceImageLoader imageLoader;
    private final Map<Integer, VisualPiece> activeVisualPieces = new HashMap<>();

    public ImgRenderer(String boardPath, int cellSize, PieceImageLoader imageLoader) {
        this.boardPath = boardPath;
        this.cellSize = cellSize;
        this.imageLoader = imageLoader;
    }

    public Img drawGame(GameSnapshot snapshot) {
        Img frameCanvas = new Img().read(boardPath);
        long frameTime = System.currentTimeMillis();

        // 1. עדכון פיזיקלי ומעברי סטייטים
        updateVisualPieces(snapshot.pieces(), frameTime);

        // 2. רינדור הכלים
        for (PieceSnapshot piece : snapshot.pieces()) {
            VisualPiece visual = activeVisualPieces.get(piece.id());

            // אם הכלי עבר לסטייט פנימי כמו LONG_REST, נשתמש בסטייט הויזואלי הנוכחי שלו
            State currentState = (visual != null) ? visual.lastState : piece.state();
            long startTime = (visual != null) ? visual.stateStartTime : frameTime;

            GenericFrameState frameState = getFrameStateHelper(currentState);
            AnimationConfig config = imageLoader.getAnimation(piece.color(), piece.type(), frameState.getFolderName());

            Img pieceImg = frameState.getFrame(config, startTime, frameTime);

            if (pieceImg != null) {
                int x = (visual != null) ? (int) visual.currentX : piece.position().getColumn() * cellSize + 10;
                int y = (visual != null) ? (int) visual.currentY : piece.position().getRow() * cellSize + 10;

                pieceImg.drawOn(frameCanvas, x, y);

                if (currentState != State.IDLE) {
                    frameCanvas.putText(currentState.toString(), x + 5, y + cellSize - 5, 0.8f, Color.RED, 1);
                }
            }
        }
        return frameCanvas;
    }

    private void updateVisualPieces(List<PieceSnapshot> snapshots, long frameTime) {
        Set<Integer> activeIds = new HashSet<>();

        for (PieceSnapshot snapshot : snapshots) {
            int id = snapshot.id();
            activeIds.add(id);

            int targetX = snapshot.targetPosition().getColumn() * cellSize + 10;
            int targetY = snapshot.targetPosition().getRow() * cellSize + 10;

            VisualPiece visual = activeVisualPieces.get(id);

            if (visual == null) {
                int startX = snapshot.position().getColumn() * cellSize + 10;
                int startY = snapshot.position().getRow() * cellSize + 10;
                visual = new VisualPiece(startX, startY, snapshot.state(), frameTime);
                activeVisualPieces.put(id, visual);
            } else {
                // בדיקה האם ההחלקה הפיזית הסתיימה בדיוק עכשיו
                long elapsedPhysics = frameTime - visual.moveStartTime;
                boolean physicsFinished = elapsedPhysics >= (visual.targetX == visual.startX && visual.targetY == visual.startY ? 0 : 300); // משך ההחלקה

                if (physicsFinished && (visual.lastState == State.MOVING || visual.lastState == State.JUMPING)) {
                    // שליפת ה-config של הסטייט שהסתיים כדי לדעת מה הסטייט הבא
                    GenericFrameState prevFrameState = getFrameStateHelper(visual.lastState);
                    AnimationConfig config = imageLoader.getAnimation(snapshot.color(), snapshot.type(), prevFrameState.getFolderName());

                    if (config != null && config.nextStateWhenFinished != null) {
                        // המרה של המחרוזת מה-JSON (למשל "long_rest") ל-Enum State
                        State nextState = convertStringToState(config.nextStateWhenFinished);
                        if (nextState != visual.lastState) {
                            visual.lastState = nextState;
                            visual.stateStartTime = frameTime; // איפוס השעון בשביל האנימציה החדשה של המנוחה!
                        }
                    }
                }

                // בדיקה אם הגיע עדכון סטייט חדש לחלוטין מהשרת/מנוע שמבטל את המנוחה
                if (visual.lastState != snapshot.state() && snapshot.state() != State.IDLE) {
                    visual.lastState = snapshot.state();
                    visual.stateStartTime = frameTime;
                }

                // עדכון יעד תנועה חדש במידה והכלי זז
                if (visual.targetX != targetX || visual.targetY != targetY) {
                    GenericFrameState frameState = getFrameStateHelper(snapshot.state());
                    AnimationConfig config = imageLoader.getAnimation(snapshot.color(), snapshot.type(), frameState.getFolderName());
                    long duration = (config != null) ? config.getTotalDuration() : 300;

                    visual.setNewTarget(targetX, targetY, duration, frameTime);
                }
            }
            visual.updatePosition(frameTime);
        }
        activeVisualPieces.keySet().retainAll(activeIds);
    }

    private GenericFrameState getFrameStateHelper(State state) {
        switch (state) {
            case JUMPING:    return new GenericFrameState("jump");
            case MOVING:     return new GenericFrameState("move");
            case LONG_REST:  return new GenericFrameState("long_rest");
            case SHORT_REST: return new GenericFrameState("short_rest");
            case IDLE:
            default:         return new GenericFrameState("idle");
        }
    }

    private State convertStringToState(String stateStr) {
        if (stateStr == null) return State.IDLE;
        switch (stateStr.toLowerCase().trim()) {
            case "long_rest":  return State.LONG_REST;
            case "short_rest": return State.SHORT_REST;
            case "move":       return State.MOVING;
            case "jump":       return State.JUMPING;
            case "idle":
            default:           return State.IDLE;
        }
    }
}