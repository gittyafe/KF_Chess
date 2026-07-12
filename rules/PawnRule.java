package rules;

import models.Board;
import models.Piece;
import models.PieceFactory;

public class PawnRule implements IPieceRule {

    @Override
    public boolean isLegalMove(int sr, int sc, int tr, int tc, Piece piece, Board board) {
        Piece targetPiece = board.queryPieceAt(tr, tc);
        char color = piece.getColor();

        // Direction: white goes up (-1), black goes down (+1)
        int requiredRowDirection = (color == 'w') ? -1 : 1;
        int actualRowDirection = tr - sr;
        int deltaCol = Math.abs(tc - sc);
        int startingRank = color == 'w' ? board.getHeight() - 2 : 1;

        // --- Forward movement ---
        if (sc == tc) {
            // Target must be empty for forward movement
            if (targetPiece != null)
                return false;

            // Single step forward
            if (actualRowDirection == requiredRowDirection) {
                return true;
            }

            // Double step from starting position
            if (actualRowDirection == 2 * requiredRowDirection) {
                if (sr == startingRank) {
                    int middleRow = sr + requiredRowDirection;
                    return board.queryPieceAt(middleRow, sc) == null;
                }
            }
            return false;
        }

        // --- Diagonal capture ---
        if (deltaCol == 1 && actualRowDirection == requiredRowDirection) {
            return targetPiece != null;
        }

        return false;
    }

    @Override
    public Piece onReachEdge(Piece piece, int targetRow) {
        // // Pawn reaches opposite edge -> promotes to Queen
        // int lastRowForWhite = 0;
        // int lastRowForBlack = board.getHeight() - 1;

        // if ((piece.getColor() == 'w' && targetRow == lastRowForWhite) ||
        // (piece.getColor() == 'b' && targetRow == lastRowForBlack)) { // Simple check
        // for now
        // // Return pawn promoted to Queen
        // return PieceFactory.createPiece(piece.getColor(), 'Q', piece.getPosition());
        // }
        return piece;
    }
}
