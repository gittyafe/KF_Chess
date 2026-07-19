package org.example.controllers;

import org.example.models.MoveRequest;
import org.example.models.Position;
import org.example.engines.GameEngine;

/**
 * Manages piece selection and move initiation.
 */
public class Controller {

    private boolean isSelected = false;
    private Position selectedPosition = new Position(-1, -1);
    private final GameEngine gameEngine;

    public Controller(GameEngine gameEngine) {
        this.gameEngine = gameEngine;
    }

    /**
     * Process a click at a logical board cell and either select a piece or
     * attempt a move.
     *
     * @param col logical column index on the board (0-based)
     * @param row logical row index on the board (0-based)
     */
    public void click(int col, int row) {
        Position targetPosition = new Position(row, col);

        if (!isSelected) {
            if (gameEngine.getPieceAt(targetPosition) != null) {
                isSelected = true;
                selectedPosition = targetPosition;
            }
            return;
        }

        MoveRequest moveResult = gameEngine.requestMove(selectedPosition, targetPosition);
        switch (moveResult.getReason()) {
            case SUCCESS:
                clearSelection();
                break;

            case SAME_COLOR_OCCUPIED:
                selectedPosition = targetPosition;
                break;

            default:
                clearSelection();
                break;
        }
    }

    /**
     * Clear the currently selected piece.
     */
    public void clearSelection() {
        isSelected = false;
        selectedPosition = new Position(-1, -1);
    }

    /**
     * Start a jump action for the piece at the given logical board cell.
     *
     * @param col logical column index on the board (0-based)
     * @param row logical row index on the board (0-based)
     */
    public void jump(int col, int row) {
        gameEngine.jumpRequest(new Position(row, col));
    }

    public void wait_(long ms){
        gameEngine.wait_(ms);
    }
}
