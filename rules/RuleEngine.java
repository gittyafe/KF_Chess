package rules;

import models.Board;
import models.Piece;
import models.Position;

public class RuleEngine {
  
    public static boolean isValidMove(Piece piece, Position src, Position dest, Board board) {
        int sr = src.getRow();
        int sc = src.getColumn();
        int tr = dest.getRow();
        int tc = dest.getColumn();
        return piece.getPieceRule().isLegalMove(sr, sc, tr, tc, piece, board);
    }

    public Piece onReachEdge(Piece piece, int targetRow) {
        return piece.getPieceRule().onReachEdge(piece, targetRow);
    }

}
