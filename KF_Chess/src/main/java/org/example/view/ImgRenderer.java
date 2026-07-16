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

private void updateVisualPieces(List<PieceSnapshot> snapshots, long frameTime) {
    Set<Integer> activeIds = new HashSet<>();

    for (PieceSnapshot snapshot : snapshots) {
        int id = snapshot.id();
        activeIds.add(id);

        int targetX = snapshot.targetPosition().getColumn() * cellSize + 10;
        int targetY = snapshot.targetPosition().getRow() * cellSize + 10;

        VisualPiece visual = activeVisualPieces.get(id);

        if (visual == null) {
            visual = new VisualPiece(targetX, targetY, snapshot.state(), frameTime);
            activeVisualPieces.put(id, visual);
        } else {
            // עדכון סטייט במידה והשתנה (ה-Renderer מקשיב למנוע!)
            if (visual.state != snapshot.state()) {
                visual.state = snapshot.state();
                visual.stateStartTime = frameTime;
            }

            // עדכון יעד רק אם המיקום ב-Snapshot השתנה
            if (visual.targetX != targetX || visual.targetY != targetY) {
                // כאן אפשר להוסיף לוגיקה לבחירת משך זמן לפי ה-State
                long duration = getDurationForState(snapshot);
                visual.setNewTarget(targetX, targetY, duration, frameTime);
            }
        }
        // תמיד מעדכנים את המיקום הפיזי
        visual.updatePosition(frameTime);
    }
    activeVisualPieces.keySet().retainAll(activeIds);
}

    private long getDurationForState(PieceSnapshot snapshot) {
        GenericFrameState fs = getFrameStateHelper(snapshot.state());
        AnimationConfig cfg = imageLoader.getAnimation(snapshot.color(), snapshot.type(), fs.getFolderName());
        return (cfg != null) ? cfg.getTotalDuration() : 300;
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
            State currentState = piece.state();
            System.out.println("Piece ID: " + piece.id() + ", Engine State: " + piece.state() + ", Visual State: " + (visual != null ? visual.state : "N/A"));
            long startTime = (visual != null) ? visual.stateStartTime : frameTime;

            GenericFrameState frameState = getFrameStateHelper(currentState);
            AnimationConfig config = imageLoader.getAnimation(piece.color(), piece.type(), frameState.getFolderName());

            Img pieceImg = frameState.getFrame(config, startTime, frameTime);

            if (pieceImg != null) {
                int x = (visual != null) ? (int) visual.currentX : piece.position().getColumn() * cellSize + 10;
                int y = (visual != null) ? (int) visual.currentY : piece.position().getRow() * cellSize + 10;

                pieceImg.drawOn(frameCanvas, x, y);

                frameCanvas.putText(currentState.toString(), x + 5, y + cellSize - 5, 0.8f, Color.RED, 1);
            }
            // בתוך הלולאה ב-drawGame:

        }
        return frameCanvas;
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

}