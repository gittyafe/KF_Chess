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
import org.example.view.states.*;

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
        // 1. טעינת קנבס רקע הלוח המקורי
        Img frameCanvas = new Img().read(boardPath);

        // 2. חישוב שעון מרכזי יחיד עבור כל הפריים הנוכחי
        long frameTime = System.currentTimeMillis();

        // 3. עדכון המיקומים הפיזיים לפי מהירויות ה-Physics המוגדרות ב-JSON
        updateVisualPieces(snapshot.pieces(), frameTime);

        // 4. ציור כל הכלים על הלוח בצורה מסונכרנת
        for (PieceSnapshot piece : snapshot.pieces()) {
            VisualPiece visual = activeVisualPieces.get(piece.id());
            long startTime = (visual != null) ? visual.stateStartTime : frameTime;

            PieceFrameState frameState = getFrameStateHelper(piece.state());
            AnimationConfig config = imageLoader.getAnimation(piece.color(), piece.type(), frameState.getFolderName());

            // שליפת הפריים תוך שימוש בשעון האחיד
            Img pieceImg = frameState.getFrame(config, startTime, frameTime);

            if (pieceImg != null) {
                int x = (visual != null) ? (int) visual.currentX : piece.position().getColumn() * cellSize + 10;
                int y = (visual != null) ? (int) visual.currentY : piece.position().getRow() * cellSize + 10;

                pieceImg.drawOn(frameCanvas, x, y);

                if (piece.state() != State.IDLE) {
                    frameCanvas.putText(piece.state().toString(), x + 5, y + cellSize - 5, 0.8f, Color.RED, 1);
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
                visual = new VisualPiece(startX, startY, frameTime);
                visual.lastState = snapshot.state();
                activeVisualPieces.put(id, visual);
            } else {
                if (visual.targetX != targetX || visual.targetY != targetY ||
                        (snapshot.state() == State.MOVING && visual.lastState != State.MOVING)) {

                    visual.lastState = snapshot.state();

                    PieceFrameState frameState = getFrameStateHelper(snapshot.state());
                    AnimationConfig config = imageLoader.getAnimation(snapshot.color(), snapshot.type(), frameState.getFolderName());

                    // חישוב אורך תנועת ההחלקה הפיזית מתוך קובץ ה-JSON הנטען של הסטייט!
                    long duration = (config != null) ? config.getTotalDuration() : 300;

                    visual.setNewTarget(targetX, targetY, duration, frameTime);
                }
            }
            visual.updatePosition(frameTime);
        }
        activeVisualPieces.keySet().retainAll(activeIds);
    }

    private PieceFrameState getFrameStateHelper(State state) {
        switch (state) {
            case JUMPING: return new JumpingFrameState();
            case MOVING:  return new MovingFrameState();
            case IDLE:
            default:      return new IdleFrameState();
        }
    }
}