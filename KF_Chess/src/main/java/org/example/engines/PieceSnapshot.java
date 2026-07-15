package org.example.engines;

import org.example.models.Position;
import org.example.models.State;

/**
 * Snapshot of a piece's current state for rendering.
 */
public record PieceSnapshot(
        int id,
        char type,
        char color,
        Position position,       // המיקום הנוכחי (או המקור בזמן תנועה)
        Position targetPosition, // <-- המיקום אליו הכלי אמור להגיע!
        State state
) {
    public String toAssetKey() {
        return color + "_" + Character.toUpperCase(type);
    }
}