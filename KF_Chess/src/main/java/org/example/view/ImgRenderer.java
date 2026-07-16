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

/**
 * גרסה מתוקנת וממוטבת של מחלקת ImgRenderer.
 * פותרת את בעיית שיגור הכלים ומחילה את האנימציות כראוי.
 */
public class ImgRenderer {
    private static final long DEFAULT_ANIMATION_DURATION_MS = 300;
    private static final float STATE_LABEL_FONT_SIZE = 0.8f;

    private final String boardPath;
    private final BoardGeometry geometry;
    private final PieceImageLoader imageLoader;
    private final Map<Integer, VisualPiece> activeVisualPieces = new HashMap<>();

    public ImgRenderer(String boardPath, BoardGeometry geometry, PieceImageLoader imageLoader) {
        this.boardPath = boardPath;
        this.geometry = geometry;
        this.imageLoader = imageLoader;
    }

    public Img drawGame(GameSnapshot snapshot) {
        Img frameCanvas = loadBoardCanvas();
        long frameTime = System.currentTimeMillis();

        updateVisualPieces(snapshot.pieces(), frameTime);

        for (PieceSnapshot piece : snapshot.pieces()) {
            renderPiece(frameCanvas, piece, frameTime);
        }
        return frameCanvas;
    }

    private Img loadBoardCanvas() {
        int size = geometry.getBoardSizePx();
        return new Img().read(boardPath, new java.awt.Dimension(size, size), false, null);
    }

    private void renderPiece(Img frameCanvas, PieceSnapshot piece, long frameTime) {
        VisualPiece visual = activeVisualPieces.get(piece.id());
        State currentState = piece.state();
        long startTime = (visual != null) ? visual.stateStartTime : frameTime;

        GenericFrameState frameState = getFrameStateHelper(currentState);
        AnimationConfig config = imageLoader.getAnimation(piece.color(), piece.type(), frameState.getFolderName());

        Img pieceImg = frameState.getFrame(config, startTime, frameTime);
        if (pieceImg == null) {
            return;
        }

        int x = resolveX(visual, piece);
        int y = resolveY(visual, piece);

        pieceImg.drawOn(frameCanvas, x, y);
        frameCanvas.putText(currentState.toString(), x + 5, y + geometry.getCellSize() - 5,
                STATE_LABEL_FONT_SIZE, Color.RED, 1);
    }

    private int resolveX(VisualPiece visual, PieceSnapshot piece) {
        return (visual != null) ? (int) visual.currentX : geometry.pixelX(piece.position().getColumn());
    }

    private int resolveY(VisualPiece visual, PieceSnapshot piece) {
        return (visual != null) ? (int) visual.currentY : geometry.pixelY(piece.position().getRow());
    }

    /**
     * תיקון הבאג המרכזי:
     * 1. אתחול הכלים במיקום ההתחלתי שלהם (מתוך snapshot.position()) במקום ישר ביעד.
     * 2. ביטול ה-continue המוקדם שגרם לדילוג על תחילת התנועה.
     * 3. הוספת Fallback לזמן תנועה תקין (מינימום 100 מילישניות).
     */
    private void updateVisualPieces(List<PieceSnapshot> snapshots, long frameTime) {
        Set<Integer> activeIds = new HashSet<>();

        for (PieceSnapshot snapshot : snapshots) {
            int id = snapshot.id();
            activeIds.add(id);

            // נקודת המוצא הנוכחית בפיקסלים
            int startX = geometry.pixelX(snapshot.position().getColumn());
            int startY = geometry.pixelY(snapshot.position().getRow());

            // נקודת היעד הסופית בפיקסלים
            int targetX = geometry.pixelX(snapshot.targetPosition().getColumn());
            int targetY = geometry.pixelY(snapshot.targetPosition().getRow());

            VisualPiece visual = activeVisualPieces.get(id);
            if (visual == null) {
                // יוצרים את הכלי החדש במיקום המוצא שלו (ולא ביעד)
                visual = new VisualPiece(startX, startY, snapshot.state(), frameTime);
                activeVisualPieces.put(id, visual);
                // לא עושים continue, אלא ממשיכים לבדוק אם הוא צריך לזוז ליעד חדש
            }

            if (visual.state != snapshot.state()) {
                visual.state = snapshot.state();
                visual.stateStartTime = frameTime;
            }

            // עדכון היעד יתבצע רק אם חל שינוי אמיתי בקואורדינטות היעד
            if (visual.targetX != targetX || visual.targetY != targetY) {
                long duration = getDurationForState(snapshot);

                // מניעת באג משך תנועה אפסי (Zero Duration Bug)
                if (duration <= 10) {
                    duration = DEFAULT_ANIMATION_DURATION_MS;
                }

                visual.setNewTarget(targetX, targetY, duration, frameTime);
            }

            visual.updatePosition(frameTime);
        }
        activeVisualPieces.keySet().retainAll(activeIds);
    }

    private long getDurationForState(PieceSnapshot snapshot) {
        GenericFrameState fs = getFrameStateHelper(snapshot.state());
        AnimationConfig cfg = imageLoader.getAnimation(snapshot.color(), snapshot.type(), fs.getFolderName());
        return (cfg != null) ? cfg.getTotalDuration() : DEFAULT_ANIMATION_DURATION_MS;
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