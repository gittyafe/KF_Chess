package org.example.controllers;

import org.example.engines.GameEngine;
import org.example.models.MoveRequest;
import org.example.models.Position;

public class LocalController implements ChessController {

    private final GameEngine gameEngine;
    private final SelectionManager selectionManager = new SelectionManager();

    public LocalController(GameEngine gameEngine) {
        this.gameEngine = gameEngine;
    }

    @Override
    public void click(int col, int row) {
        Position targetPosition = new Position(row, col);

        // 1. אם עדיין לא נבחר כלי
        if (!selectionManager.isSelected()) {
            if (gameEngine.getPieceAt(targetPosition) != null) {
                selectionManager.select(targetPosition);
            }
            return;
        }

        // 2. מבצעים בקשת מהלך סנכרונית מול מנוע המשחק המקומי
        MoveRequest moveResult = gameEngine.requestMove(
                selectionManager.getSelectedPosition(),
                targetPosition
        );

        // 3. טיפול בתוצאת המהלך המיידית
        switch (moveResult.getReason()) {
            case SUCCESS:
                selectionManager.clear();
                break;

            case SAME_COLOR_OCCUPIED:
                selectionManager.select(targetPosition);
                break;

            default:
                selectionManager.clear();
                break;
        }
    }

    @Override
    public void jump(int col, int row) {
        Position position = new Position(row, col);
        gameEngine.jumpRequest(position);
    }

    @Override
    public void clearSelection() {
        selectionManager.clear();
    }

    public void wait_(long ms) {
        if (gameEngine != null) {
            gameEngine.wait_(ms);
        }
    }
}