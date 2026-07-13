package textIO;

import org.example.models.Board;
import org.example.models.Piece;
import org.example.models.Position;
import org.example.textIO.BoardPrinter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BoardPrinterTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    @BeforeEach
    void setUpStreams() {
        System.setOut(new PrintStream(outContent));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
    }

    @Test
    @DisplayName("Print should output the exact structural text representation of the board")
    void print_ValidBoard_OutputsCorrectText() {
        // Arrange
        Board board = new Board(3, 2); // רוחב 3, גובה 2
        board.addPiece(new Piece(1, 'w', 'K', new Position(0, 0)));
        board.addPiece(new Piece(2, 'b', 'Q', new Position(1, 2)));

        // Act
        BoardPrinter.print(board);

        // מחליפים את סיומות השורות של ווינדוס (\r\n) ל-\n אחיד לצורך הבדיקה
        String actualOutput = outContent.toString().replace("\r\n", "\n");
        String expectedOutput = "wK . .\n" +
                ". . bQ\n";

        // Assert
        assertEquals(expectedOutput, actualOutput);
    }

    @Test
    @DisplayName("Print with null board reference should produce no console output")
    void print_NullBoard_ProducesNoOutput() {
        BoardPrinter.print(null);
        assertEquals("", outContent.toString());
    }
}