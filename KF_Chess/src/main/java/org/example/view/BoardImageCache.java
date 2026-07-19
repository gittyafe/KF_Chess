package org.example.view;

import java.awt.Dimension;

/**
 * Loads the board background image from disk once per size and hands out
 * cheap copies for each frame.
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
