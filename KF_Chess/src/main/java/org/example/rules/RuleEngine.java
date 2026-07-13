package org.example.rules;

import org.example.models.Board;
import org.example.models.Piece;
import org.example.models.Position;

public class RuleEngine {

    /**
     * Validate whether a move is legal for the given piece on the specified board.
     */
    public static boolean isValidMove(Piece piece, Position src, Position dest, Board board) {
        Piece targetPiece = board.queryPieceAt(dest);
        boolean isCapture = (targetPiece != null);

        if (!MoveGeometry.isDirectionValid(piece.getType(), src, dest, piece.getColor(), isCapture,
                board.getHeight())) {
            return false;
        }

        if (requiresPathClearCheck(piece.getType()) && !isPathClear(src, dest, board)) {
            return false;
        }

        if (!checkContextualObstacles(piece, src, dest, board)) {
            return false;
        }

        return true;
    }

    private static boolean requiresPathClearCheck(char type) {
        char t = Character.toUpperCase(type);
        return t == 'R' || t == 'B' || t == 'Q';
    }

    /**
     * Generic path blocking check for sliding pieces.
     */
    private static boolean isPathClear(Position src, Position dest, Board board) {
        int stepRow = Integer.compare(dest.getRow(), src.getRow());
        int stepCol = Integer.compare(dest.getColumn(), src.getColumn());

        int currRow = src.getRow() + stepRow;
        int currCol = src.getColumn() + stepCol;

        while (currRow != dest.getRow() || currCol != dest.getColumn()) {
            if (board.queryPieceAt(currRow, currCol) != null) {
                return false;
            }
            currRow += stepRow;
            currCol += stepCol;
        }
        return true;
    }

    /**
     * Perform board-dependent obstacle checks for special movement rules.
     */
    private static boolean checkContextualObstacles(Piece piece, Position src, Position dest, Board board) {
        char type = Character.toUpperCase(piece.getType());

        if (type == 'P' && Math.abs(dest.getRow() - src.getRow()) == 2) {
            int stepRow = (piece.getColor() == 'w') ? -1 : 1;
            if (board.queryPieceAt(src.getRow() + stepRow, src.getColumn()) != null) {
                return false;
            }
        }

        return true;
    }
}