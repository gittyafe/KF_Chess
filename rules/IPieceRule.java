package rules;

import models.Board;
import models.Piece;

/**
 * Interface that defines how a piece type moves and behaves
 * Each piece type (King, Rook, Pawn, etc.) implements this
 */
public interface IPieceRule {

    /**
     * Determines if this piece type can move from (sr, sc) to (tr, tc)
     * 
     * @param sr    source row
     * @param sc    source col
     * @param tr    target row
     * @param tc    target col
     * @param piece the piece being moved (contains color, etc)
     * @param board the current board state
     * @return true if move is legal according to this piece's rules
     */
    boolean isLegalMove(int sr, int sc, int tr, int tc, Piece piece, Board board);

    /**
     * What happens when this piece reaches the opposite edge?
     * (e.g., pawn promotion)
     * 
     * @param piece     the piece reaching the edge
     * @param targetRow the row the piece reached
     * @return the piece to place (could be transformed or unchanged)
     */
    Piece onReachEdge(Piece piece, int targetRow);
}
