package org.example.models;

public class PieceFactory {
    private static int id = 0;

    public static Piece createPiece(char color, char type, Position pos) {
        return new Piece(id++, color, Character.toUpperCase(type), pos);
    }
}
