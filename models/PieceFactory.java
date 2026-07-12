package models;

import rules.*;

public class PieceFactory {

    // שמירת מופע יחיד (Singleton-like) מכל אסטרטגיה כדי לחסוך בזיכרון
    private static final IPieceRule rookRule = new RookRule();

    public static Piece createPiece(char color, char type , Position position) {
        switch (Character.toUpperCase(type)) {
            case 'R':
                return new Piece(color, 'R', position, rookRule);
            // case 'K':
            //     return new Piece('K', color, kingRule);
            // case 'P':
            //     return new Piece('P', color, pawnRule);
            
            // כאן קל מאוד להוסיף כלים מיוחדים של KF Chess בעתיד:
            // case 'X': 
            //     return new Piece('X', color, specialKFStrategy);

            default:
                throw new IllegalArgumentException("Unknown piece type: " + type);
        }
    }
}