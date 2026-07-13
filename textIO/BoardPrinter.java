package textIO;

import models.Board;
import models.Piece;
import models.Position;

/**
 * Simple board printer for console output.
 */
public class BoardPrinter {

    /**
     * Print the board state to standard output.
     *
     * @param board board instance to print
     */
    public static void print(Board board) {
        if (board == null) {
            return;
        }

        int height = board.getHeight();
        int width = board.getWidth();

        for (int row = 0; row < height; row++) {
            StringBuilder line = new StringBuilder();
            for (int col = 0; col < width; col++) {
                Piece piece = board.queryPieceAt(new Position(row, col));
                String token = ".";
                if (piece != null) {
                    token = String.valueOf(piece.getColor()) + piece.getType();
                }
                line.append(token);
                if (col < width - 1) {
                    line.append(" ");
                }
            }
            System.out.println(line.toString());
        }
    }
}
