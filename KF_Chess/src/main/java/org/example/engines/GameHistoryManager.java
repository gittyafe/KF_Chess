package org.example.engines;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Keeps the move history for both players. Listens to {@link MoveListener}
 * events coming from the {@link GameEngine} and appends to the relevant
 * player's list.
 *
 * <p>The two lists are intentionally private: previously they were public
 * fields, which let any caller (e.g. the rendering layer) mutate game
 * history directly. Callers now only get a read-only view via
 * {@link #getWhiteMoves()} / {@link #getBlackMoves()}.</p>
 */
public class GameHistoryManager implements MoveListener {
    private final List<MoveEntry> whiteMoves = new ArrayList<>();
    private final List<MoveEntry> blackMoves = new ArrayList<>();

    @Override
    public void onMoveAdded(String time, String move, char color) {
        MoveEntry entry = new MoveEntry(time, move);
        if (Character.toLowerCase(color) == 'w') {
            whiteMoves.add(entry);
        } else {
            blackMoves.add(entry);
        }
    }

    public List<MoveEntry> getWhiteMoves() {
        return Collections.unmodifiableList(whiteMoves);
    }

    public List<MoveEntry> getBlackMoves() {
        return Collections.unmodifiableList(blackMoves);
    }
}
