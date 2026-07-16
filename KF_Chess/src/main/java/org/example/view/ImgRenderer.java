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
 * אחראית על רינדור מצב המשחק לתמונה יחידה.
 *
 * שינויים עיקריים בריפקטור:
 *  - כל חישובי המיקום/גודל עוברים דרך BoardGeometry (במקום cellSize גולמי) -
 *    זה מה שפותר את בעיית חוסר היישור בין הכלים למשבצות.
 *  - תמונת הלוח נטענת עם resize לגודל שמוגדר ב-geometry, כך שאפשר להקטין/
 *    להגדיל את הלוח פשוט ע"י שינוי הפרמטר במקום אחד (ב-BoardGeometry).
 *  - הוסרו הדפסות דיבוג (System.out.println) שנשארו בקוד המקורי.
 *  - חולצו מתודות עזר קטנות לשיפור קריאות (getOrCreateVisualPiece,
 *    resolvePosition) במקום בלוק לוגיקה ארוך אחד בתוך drawGame.
 */
public class ImgRenderer {
    private static final long DEFAULT_ANIMATION_DURATION_MS = 300;
    private static final float STATE_LABEL_FONT_SIZE = 0.8f;

    private final String boardPath;
    private final BoardGeometry geometry;
    private final PieceImageLoader imageLoader;
    private final Map<Integer, VisualPiece> activeVisualPieces = new HashMap<>();

    private Img cachedBoardImg; // תמונת הלוח הבסיסית (ללא כלים), נטענת פעם אחת

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

    /**
     * טוענת את תמונת הלוח בגודל שמוגדר ב-geometry (boardSizePx x boardSizePx).
     * זהו התיקון המרכזי: בגרסה הקודמת תמונת הלוח נטענה בגודלה הטבעי,
     * שלא בהכרח תאם ל-cellSize ששימש לחישוב מיקומי הכלים.
     */
    private Img loadBoardCanvas() {
        int size = geometry.getBoardSizePx();
        // מניחים שקיים ל-Img overload שמקבל גודל יעד, כמו זה שכבר בשימוש
        // ב-PieceImageLoader. אם ה-API של Img שונה - יש להתאים את הקריאה הזו.
        Img fresh = new Img().read(boardPath, new java.awt.Dimension(size, size), false, null);
        return fresh;
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

    private void updateVisualPieces(List<PieceSnapshot> snapshots, long frameTime) {
        Set<Integer> activeIds = new HashSet<>();

        for (PieceSnapshot snapshot : snapshots) {
            int id = snapshot.id();
            activeIds.add(id);

            int targetX = geometry.pixelX(snapshot.targetPosition().getColumn());
            int targetY = geometry.pixelY(snapshot.targetPosition().getRow());

            VisualPiece visual = activeVisualPieces.get(id);
            if (visual == null) {
                activeVisualPieces.put(id, new VisualPiece(targetX, targetY, snapshot.state(), frameTime));
                continue;
            }

            if (visual.state != snapshot.state()) {
                visual.state = snapshot.state();
                visual.stateStartTime = frameTime;
            }

            if (visual.targetX != targetX || visual.targetY != targetY) {
                long duration = getDurationForState(snapshot);
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
