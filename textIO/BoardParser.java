package textIO;

import java.util.ArrayList;
import java.util.List;

import models.Board;
import models.Piece;
import models.PieceFactory;
import models.Position;

public class BoardParser {

    /**
     * Parse a board layout string into a Board instance.
     *
     * @param boardString raw board text
     * @return parsed board
     */
    public static Board parse(String boardString) {
        Board board = new Board();
        if (boardString == null || boardString.trim().isEmpty()) {
            return board;
        }

        String[] lines = boardString.split("\\r?\\n");
        List<String[]> rows = new ArrayList<>();
        int width = 0;

        for (String line : lines) {
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            String[] tokens = line.trim().split("\\s+");
            rows.add(tokens);
            if (width != 0 && width != tokens.length)
                throw new IllegalArgumentException("ERROR ROW_WIDTH_MISMATCH");
            width = tokens.length;
        }

        board.setHeight(rows.size());
        board.setWidth(width);

        for (int row = 0; row < rows.size(); row++) {
            String[] tokens = rows.get(row);
            for (int col = 0; col < tokens.length; col++) {
                String token = tokens[col];
                if (token.equals(".")) {
                    continue;
                } else if (!isValidToken(token)) {
                    throw new IllegalArgumentException("ERROR UNKNOWN_TOKEN");
                }

                Piece piece = parsePieceToken(token, row, col);
                board.addPiece(piece);

            }
        }

        return board;
    }

    private static Piece parsePieceToken(String token, int row, int col) {
        char color = token.charAt(0);
        char type = token.charAt(1);
        return PieceFactory.createPiece(color, type, new Position(row, col));
    }

    /**
     * Validate a token from board input (e.g., "wK", "bR", ".")
     */
    public static boolean isValidToken(String token) {
        if (token.equals("."))
            return true;
        if (token.length() != 2)
            return false;
        char color = token.charAt(0);
        char piece = token.charAt(1);
        return (color == 'w' || color == 'b') &&
                (piece == 'K' || piece == 'Q' || piece == 'R' || piece == 'B' || piece == 'N' || piece == 'P');
    }
}