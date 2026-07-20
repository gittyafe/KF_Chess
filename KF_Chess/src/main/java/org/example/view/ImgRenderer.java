package org.example.view;

import java.awt.Color;
import java.awt.Dimension;

import org.example.engines.GameSnapshot;
import org.example.engines.PieceSnapshot;
import org.example.models.State;

/**
 * Draws one frame of the board: the cached background plus every piece at
 * its current (possibly mid-animation) position.
 */
public class ImgRenderer {
    private static final float STATE_LABEL_FONT_SIZE = 1.0f;
    private static final int STATE_LABEL_TEXT_MARGIN_PX = 5;

    private final BoardGeometry geometry;
    private final PieceImageLoader imageLoader;
    private final BoardImageCache boardImageCache;
    private final PieceAnimationTracker animationTracker;

    public ImgRenderer(String boardPath, BoardGeometry geometry, PieceImageLoader imageLoader) {
        this.geometry = geometry;
        this.imageLoader = imageLoader;
        this.boardImageCache = new BoardImageCache(boardPath);
        this.animationTracker = new PieceAnimationTracker(this::durationForState);
    }

    public Img drawGame(GameSnapshot snapshot) {
        return drawGame(snapshot, System.currentTimeMillis());
    }

    public Img drawGame(GameSnapshot snapshot, long frameTime) {
        int size = geometry.getBoardSizePx();

        animationTracker.update(snapshot.pieces(), frameTime);

        Img boardFrame = boardImageCache.isolatedCopy(size);

        for (PieceSnapshot piece : snapshot.pieces()) {
            drawPiece(boardFrame, piece, size, frameTime);
        }

        return boardFrame;
    }

    private void drawPiece(Img boardFrame, PieceSnapshot piece, int boardSizePx) {
        drawPiece(boardFrame, piece, boardSizePx, System.currentTimeMillis());
    }

    private void drawPiece(Img boardFrame, PieceSnapshot piece, int boardSizePx, long frameTime) {
        long stateStartTime = animationTracker.stateStartTimeOf(piece.id(), frameTime);

        AnimationConfig config = imageLoader.getAnimation(piece.color(), piece.type(), folderFor(piece.state()));
        if (config == null || config.getFrames().isEmpty()) return;

        Img pieceFrame = config.getCurrentFrame(stateStartTime, frameTime);
        if (pieceFrame == null) return;

        Dimension targetSize = geometry.getPieceTargetSize();
        Img resizedPiece = pieceFrame.resize(targetSize.width, targetSize.height);

        double col = animationTracker.currentColOf(piece.id(), piece.position().getColumn());
        double row = animationTracker.currentRowOf(piece.id(), piece.position().getRow());

        int x = (int) (col * geometry.getCellSize()) + (geometry.getCellSize() - targetSize.width) / 2;
        int y = (int) (row * geometry.getCellSize()) + (geometry.getCellSize() - targetSize.height) / 2;

        x = clamp(x, 0, boardSizePx - targetSize.width);
        y = clamp(y, 0, boardSizePx - targetSize.height);

        resizedPiece.drawOn(boardFrame, x, y);

        boardFrame.putText(piece.state().toString(), x + STATE_LABEL_TEXT_MARGIN_PX,
                y + geometry.getCellSize() - STATE_LABEL_TEXT_MARGIN_PX, STATE_LABEL_FONT_SIZE, Color.RED, 1);
    }

    private long durationForState(PieceSnapshot piece) {
        AnimationConfig cfg = imageLoader.getAnimation(piece.color(), piece.type(), folderFor(piece.state()));
        return (cfg != null) ? cfg.getTotalDuration() : 0;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private String folderFor(State state) {
        return switch (state) {
            case JUMPING -> "jump";
            case MOVING -> "move";
            case LONG_REST -> "long_rest";
            case SHORT_REST -> "short_rest";
            case IDLE -> "idle";
            default -> "idle";
        };
    }
}
