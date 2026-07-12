package rules;

import models.Board;
import models.Piece;

public class KnightRule implements IPieceRule {
    @Override
    public boolean isLegalMove(int sr, int sc, int tr, int tc, Piece piece, Board board) {
        // Knight moves in L-shape: 2 squares in one direction, 1 in perpendicular
        int deltaRow = Math.abs(tr - sr);
        int deltaCol = Math.abs(tc - sc);

        return (deltaRow == 1 && deltaCol == 2) || (deltaRow == 2 && deltaCol == 1);
    }

    @Override
    public Piece onReachEdge(Piece piece, int targetRow) {
        return piece; // Knight doesn't transform
    }
}
