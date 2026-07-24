package org.example.engines;

import org.example.models.Board;
import org.example.models.Piece;
import org.example.models.PieceFactory;
import org.example.models.Position;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Populates a Board from a CSV layout resource on the classpath. This used
 * to live inside GameRoom, but "how a board is laid out from a file" has
 * nothing to do with room lifecycle, networking, or scheduling -- it's a
 * board/engine setup concern.
 */
public final class BoardLoader {

    private BoardLoader() {}

    public static void loadFromClasspath(Board board, String resourcePath) {
        try (InputStream is = BoardLoader.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                System.err.println("CSV File not found in classpath: " + resourcePath);
                return;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                int rowIndex = 0;
                while ((line = reader.readLine()) != null && rowIndex < board.getHeight()) {
                    String[] cells = line.split(",", -1);
                    int colIndex = 0;
                    for (String cell : cells) {
                        if (colIndex >= board.getWidth()) break;
                        String trimmed = cell.trim();
                        if (trimmed.length() == 2) {
                            Position pos = new Position(rowIndex, colIndex);
                            Piece piece = PieceFactory.createPiece(trimmed.charAt(0), trimmed.charAt(1), pos);
                            board.addPiece(piece);
                        }
                        colIndex++;
                    }
                    rowIndex++;
                }
            }
        } catch (Exception e) {
            System.err.println("Error loading board CSV: " + e.getMessage());
        }
    }
}
