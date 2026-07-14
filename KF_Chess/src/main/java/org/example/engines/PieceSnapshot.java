package org.example.engines;


import org.example.models.Position;
import org.example.models.State;

/**
 * Snapshot of a piece's current state for rendering.
 */
public record PieceSnapshot(
        char type,
        char color,
        Position position,
        State state
) {
    public String toAssetKey() {
        return color + "_" + Character.toUpperCase(type);
    }
}