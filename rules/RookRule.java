package rules;

import models.Board;
import models.Piece;

public class RookRule implements IPieceRule {

    @Override
    public boolean isLegalMove(int sr, int sc, int tr, int tc, Piece piece, Board board) {
        if (piece == null) {
            return false;
        }

        // Rook moves in straight lines (horizontal or vertical)
        if (sr != tr && sc != tc)
            return false;
        return isPathClear(sr, sc, tr, tc, board);
    }

    private boolean isPathClear(int sr, int sc, int tr, int tc, Board board) {
        int stepRow = Integer.compare(tr, sr);
        int stepCol = Integer.compare(tc, sc);

        int currRow = sr + stepRow;
        int currCol = sc + stepCol;

        while (currRow != tr || currCol != tc) {
            if (!board.queryPieceAt(currRow, currCol).isEmpty())
                return false;
            currRow += stepRow;
            currCol += stepCol;
        }
        return true;
    }

    @Override
    public Piece onReachEdge(Piece piece, int targetRow) {
        return piece;// Rook doesn't transform
    }
}
