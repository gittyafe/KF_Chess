package org.example.view;
import java.util.ArrayList;
import java.util.List;

public class GameHistoryManager implements MoveListener {
    public final List<MoveEntry> whiteMoves = new ArrayList<>();
    public final List<MoveEntry> blackMoves = new ArrayList<>();

    @Override
    public void onMoveAdded(String time, String move, char color) {
        MoveEntry entry = new MoveEntry(time, move);
        if (color == 'w') whiteMoves.add(entry);
        else blackMoves.add(entry);
    }
}