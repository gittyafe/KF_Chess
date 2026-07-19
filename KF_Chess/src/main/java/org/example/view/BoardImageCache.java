package org.example.view;

import java.awt.Dimension;

import org.example.Img;

/**
 * Loads the board background image from disk once per size and hands out
 * cheap copies for each frame.
 *
 * <p>The base image is only re-read from disk when the board's pixel size
 * actually changes (e.g. on window resize), not on every one of the ~30
 * frames drawn per second. Every operation here goes through {@link Img}'s
 * own API ({@code read}, {@code copy}) - no raw {@code BufferedImage} /
 * {@code Graphics2D} calls, per the "only use Img" constraint.</p>
 */
public class BoardImageCache {
    private final String boardImagePath;

    private Img cachedBaseBoard;
    private int cachedSize = -1;

    public BoardImageCache(String boardImagePath) {
        this.boardImagePath = boardImagePath;
    }

    /** Returns a fresh, independent copy of the board background at the given size, safe to draw on. */
    public Img isolatedCopy(int size) {
        if (cachedBaseBoard == null || cachedSize != size) {
            cachedBaseBoard = new Img().read(boardImagePath, new Dimension(size, size), false, null);
            cachedSize = size;
        }
        return cachedBaseBoard.copy();
    }
}
