package models;

import rules.*;

public class PieceFactory {

    // שמירת מופע יחיד (Singleton-like) מכל אסטרטגיה כדי לחסוך בזיכרון
    private static final IPieceRule rookRule = new RookRule();
    private static final IPieceRule kingRule = new KingRule();
    private static final IPieceRule pawnRule = new PawnRule();
    private static final IPieceRule bishopRule = new BishopRule();
    private static final IPieceRule queenRule = new QueenRule();
    private static final IPieceRule knightRule = new KnightRule();
    private static int id;

    public static Piece createPiece(char color, char type , Position position) {
        switch (Character.toUpperCase(type)) {
            case 'R':
                return new Piece(id++, color, 'R', position, rookRule);
            case 'K':
                return new Piece(id++, color, 'K', position, kingRule);
            case 'P':
                return new Piece(id++, color, 'P', position, pawnRule);
            case 'B':
                return new Piece(id++, color, 'B', position, bishopRule);
            case 'Q':
                return new Piece(id++, color, 'Q', position, queenRule);
            case 'N':
                return new Piece(id++, color, 'N', position, knightRule);
                          
            default:
                throw new IllegalArgumentException("Unknown piece type: " + type);
        }
    }
}