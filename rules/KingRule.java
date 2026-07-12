package rules;

import models.Board;
import models.Piece;

public class KingRule implements IPieceRule {
    @Override
    public boolean isLegalMove(int sr, int sc, int tr, int tc, Piece piece, Board board) {
        // King moves exactly 1 cell in any direction
        return Math.abs(tr - sr) <= 1 && Math.abs(tc - sc) <= 1;
    }

    @Override
    public Piece onReachEdge(Piece piece, int targetRow) {
        // King doesn't transform
        return piece;
    }

}
