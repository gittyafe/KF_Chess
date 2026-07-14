package org.example.engines;

import java.util.List;

/**
 * Snapshot of the entire game state at a specific tick.
 */
public record GameSnapshot(
        List<PieceSnapshot> pieces,
        boolean isGameOver
) {}