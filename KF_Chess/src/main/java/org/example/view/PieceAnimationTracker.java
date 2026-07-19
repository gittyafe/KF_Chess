package org.example.view;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.example.engines.PieceSnapshot;
import org.example.models.State;

/**
 * Owns the animation state of every piece and advances it, frame by frame,
 * to stay in sync with the latest {@link PieceSnapshot} list from the engine.
 *
 * <p>This used to live inside {@code ImgRenderer} (as {@code activeAnimations}
 * plus a private {@code updateAnimations} method), duplicated almost exactly
 * by an unused top-level class called {@code VisualPiece}. Both are now one
 * thing: this tracker owns the state, and the per-piece state itself
 * ({@link VisualPieceState}) is nested here privately since nothing outside
 * this tracker ever needs to touch it directly.</p>
 */
public class PieceAnimationTracker {
    private static final long DEFAULT_ANIMATION_DURATION_MS = 300;
    private static final long MIN_MEANINGFUL_DURATION_MS = 10;

    private final Map<Integer, VisualPieceState> animations = new HashMap<>();
    private final AnimationDurationLookup durationLookup;

    public interface AnimationDurationLookup {
        long durationForState(PieceSnapshot piece);
    }

    public PieceAnimationTracker(AnimationDurationLookup durationLookup) {
        this.durationLookup = durationLookup;
    }

    /** Updates every tracked piece for this frame and drops entries for pieces no longer on the board. */
    public void update(List<PieceSnapshot> pieces, long frameTime) {
        Set<Integer> stillPresent = new HashSet<>();

        for (PieceSnapshot piece : pieces) {
            stillPresent.add(piece.id());
            VisualPieceState anim = animations.computeIfAbsent(piece.id(),
                    id -> new VisualPieceState(piece.position().getColumn(), piece.position().getRow(),
                            piece.state(), frameTime));

            anim.setState(piece.state(), frameTime);

            int targetCol = piece.targetPosition().getColumn();
            int targetRow = piece.targetPosition().getRow();

            if (!anim.hasTarget(targetCol, targetRow)) {
                long duration = durationLookup.durationForState(piece);
                if (duration <= MIN_MEANINGFUL_DURATION_MS) {
                    duration = DEFAULT_ANIMATION_DURATION_MS;
                }
                anim.retarget(piece.position().getColumn(), piece.position().getRow(),
                        targetCol, targetRow, duration, frameTime);
            }

            anim.update(frameTime);
        }

        animations.keySet().retainAll(stillPresent);
    }

    public long stateStartTimeOf(int pieceId, long fallback) {
        VisualPieceState anim = animations.get(pieceId);
        return anim != null ? anim.stateStartTime : fallback;
    }

    public double currentColOf(int pieceId, double fallback) {
        VisualPieceState anim = animations.get(pieceId);
        return anim != null ? anim.currentCol : fallback;
    }

    public double currentRowOf(int pieceId, double fallback) {
        VisualPieceState anim = animations.get(pieceId);
        return anim != null ? anim.currentRow : fallback;
    }

    /** Mutable per-piece animation state: current visual state, since when, and a col/row lerp toward its target. */
    private static class VisualPieceState {
        State state;
        long stateStartTime;

        long moveStartTime;
        long moveDurationMs;

        double currentCol;
        double currentRow;

        int startCol, startRow;
        int targetCol, targetRow;

        VisualPieceState(int col, int row, State state, long frameTime) {
            this.state = state;
            this.stateStartTime = frameTime;
            this.moveStartTime = frameTime;
            this.moveDurationMs = 0;
            this.startCol = col;
            this.startRow = row;
            this.targetCol = col;
            this.targetRow = row;
            this.currentCol = col;
            this.currentRow = row;
        }

        void setState(State newState, long frameTime) {
            if (this.state != newState) {
                this.state = newState;
                this.stateStartTime = frameTime;
            }
        }

        boolean hasTarget(int col, int row) {
            return targetCol == col && targetRow == row;
        }

        void retarget(int fromCol, int fromRow, int toCol, int toRow, long durationMs, long frameTime) {
            this.moveStartTime = frameTime;
            this.moveDurationMs = durationMs;
            this.startCol = fromCol;
            this.startRow = fromRow;
            this.targetCol = toCol;
            this.targetRow = toRow;
        }

        void update(long frameTime) {
            if (moveDurationMs <= 0) {
                currentCol = targetCol;
                currentRow = targetRow;
                return;
            }
            double progress = (double) (frameTime - moveStartTime) / moveDurationMs;
            progress = Math.max(0.0, Math.min(1.0, progress));

            currentCol = startCol + (targetCol - startCol) * progress;
            currentRow = startRow + (targetRow - startRow) * progress;
        }
    }
}
