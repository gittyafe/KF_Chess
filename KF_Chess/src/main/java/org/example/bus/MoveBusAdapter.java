package org.example.bus;

import org.example.bus.GameEventBus;
import org.example.engines.MoveListener;

/**
 * Implements the MoveListener interface GameEngine already exposes.
 * GameEngine.java is not modified - register via
 * gameEngine.addMoveListener(new MoveBusAdapter()) in your composition root.
 *
 * ⚠️ SHAPE MISMATCH TO DECIDE: GameEngine gives you three separate values
 * (time, moveNotation, color), but GameFrameComposer's current "MOVE_LOGGED"
 * subscriber expects a single String and passes it straight to
 * historyManager.logMove(moveText). You have two options - pick one and
 * we can adjust either side:
 *
 *   (a) Keep GameFrameComposer's subscriber as-is (single String) and have
 *       this adapter format one combined string, e.g. below. Simple, but
 *       historyManager.logMove loses the separate time/color fields.
 *
 *   (b) Change GameFrameComposer's "MOVE_LOGGED" subscriber to accept a
 *       small record (time, moveNotation, color) instead of a bare String,
 *       and let historyManager.logMove take those fields separately if it
 *       needs to show them per-color (getWhiteMoves()/getBlackMoves()
 *       suggests it already splits by color somehow).
 *
 * Option (a) is implemented below since it requires zero changes anywhere
 * else. Switch to (b) if historyManager needs the fields separately.
 */

public class MoveBusAdapter implements MoveListener {
    @Override
    public void onMoveAdded(String time, String moveNotation, char color) {
        // אורזים את שלושת הפרמטרים בדיוק כמו שהקומפוזר מצפה לקבל אותם!
        Object[] movePayload = new Object[]{ time, moveNotation, color };
        GameEventBus.getInstance().publish("MOVE_LOGGED", movePayload);
    }
}
