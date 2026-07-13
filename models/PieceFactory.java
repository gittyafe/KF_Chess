package models;

public class PieceFactory {
    private static int id = 0;

    public static Piece createPiece(char color, char type, Position pos) {
        // שורה אחת קטנה, ה-switch המיותר נמחק!
        return new Piece(id++, color, Character.toUpperCase(type), pos);
    }
}
