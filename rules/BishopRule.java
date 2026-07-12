package rules;

import models.Board;
import models.Piece;

public class BishopRule implements IPieceRule {
    @Override
    public boolean isLegalMove(int sr, int sc, int tr, int tc, Piece piece, Board board) {
        // Bishop moves diagonally
        int deltaRow = Math.abs(tr - sr);
        int deltaCol = Math.abs(tc - sc);

        if (deltaRow != deltaCol)
            return false;
        return isPathClear(sr, sc, tr, tc, board);
    }

    @Override
    public Piece onReachEdge(Piece piece, int targetRow) {
        return piece; // Bishop doesn't transform
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
