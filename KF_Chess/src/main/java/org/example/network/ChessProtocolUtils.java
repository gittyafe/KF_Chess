package org.example.network;

import org.example.engines.PieceSnapshot;
import org.example.models.Position;

public class ChessProtocolUtils {

    public static String toNotation(Position pos) {
        char file = (char) ('a' + pos.getColumn());
        char rank = (char) ('0' + (8 - pos.getRow()));
        return "" + file + rank;
    }

    public static String buildMoveCommand(PieceSnapshot piece, Position to) {
        char colorChar = Character.toUpperCase(piece.color()) == 'W' ? 'W' : 'B';
        char typeChar = Character.toUpperCase(piece.type());
        return "" + colorChar + typeChar + toNotation(piece.position()) + toNotation(to);
    }

    public static String buildJumpCommand(PieceSnapshot piece) {
        char colorChar = Character.toUpperCase(piece.color()) == 'W' ? 'W' : 'B';
        return "J" + colorChar + toNotation(piece.position());
    }
}