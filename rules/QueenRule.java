package rules;

import models.Board;
import models.Piece;

public class QueenRule implements IPieceRule {
    @Override
    public boolean isLegalMove(int sr, int sc, int tr, int tc, Piece piece, Board board) {
        // Queen moves like Rook + Bishop (any direction)
        int deltaRow = Math.abs(tr - sr);
        int deltaCol = Math.abs(tc - sc);

        // Must be straight or diagonal
        if ((sr == tr || sc == tc) || (deltaRow == deltaCol)) {
            return isPathClear(sr, sc, tr, tc, board);
        }
        return false;
    }

    @Override
    public Piece onReachEdge(Piece piece, int targetRow) {
        return piece; // Queen doesn't transform
    }


    private boolean isPathClear(int sr, int sc, int tr, int tc, Board board) {
        int stepRow = Integer.compare(tr, sr);
        int stepCol = Integer.compare(tc, sc);

        int currRow = sr + stepRow;
        int currCol = sc + stepCol;

        while (currRow != tr || currCol != tc) {
            if (board.queryPieceAt(currRow, currCol) != null)
                return false;
            currRow += stepRow;
            currCol += stepCol;
        }
        return true;
    }
}
