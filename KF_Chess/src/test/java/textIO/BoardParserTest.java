package textIO;

import org.example.models.Board;
import org.example.models.Piece;
import org.example.models.Position;
import org.example.textIO.BoardParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class BoardParserTest {

    @Nested
    @DisplayName("Successful Parsing Scenarios")
    class HappyPathTests {

        @Test
        @DisplayName("Valid board layout string should build correct board dimensions and pieces")
        void parse_ValidString_ReturnsCorrectBoard() {
            // Arrange
            String layout = "wR . bK\n" +
                    ".  wP .\n" +
                    ".  .  .";

            // Act
            Board board = BoardParser.parse(layout);

            // Assert
            assertNotNull(board);
            assertEquals(3, board.getHeight());
            assertEquals(3, board.getWidth());

            Piece whiteRook = board.queryPieceAt(new Position(0, 0));
            assertNotNull(whiteRook);
            assertEquals('w', whiteRook.getColor());
            assertEquals('R', whiteRook.getType());

            Piece blackKing = board.queryPieceAt(new Position(0, 2));
            assertNotNull(blackKing);
            assertEquals('b', blackKing.getColor());
            assertEquals('K', blackKing.getType());

            assertNull(board.queryPieceAt(new Position(0, 1)));
        }

        @Test
        @DisplayName("Empty or null input should return an empty board layout")
        void parse_NullOrEmptyInput_ReturnsEmptyBoard() {
            assertTrue(BoardParser.parse(null).isEmpty());
            assertTrue(BoardParser.parse("   ").isEmpty());
        }
    }

    @Nested
    @DisplayName("Parsing Validation & Error Scenarios")
    class ErrorPathTests {

        @Test
        @DisplayName("Should throw ROW_WIDTH_MISMATCH exception if row sizes are asymmetric")
        void parse_AsymmetricRows_ThrowsIllegalArgumentException() {
            String invalidLayout = "wR .\n" +
                    ". . bK"; // שורה שנייה באורך 3, ראשונה באורך 2

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                BoardParser.parse(invalidLayout);
            });

            assertEquals("ERROR ROW_WIDTH_MISMATCH", exception.getMessage());
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "wR x",      // טוקן לא מוכר באורך 1
                "wR wRz",    // טוקן ארוך מדי
                "wR Wk",     // אות גדולה בצבע
                "wR wX"      // סוג כלי לא חוקי (אם מוגדר ב-isValidToken)
        })
        @DisplayName("Should throw UNKNOWN_TOKEN exception for corrupted tokens")
        void parse_UnknownTokens_ThrowsIllegalArgumentException(String invalidLayout) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                BoardParser.parse(invalidLayout);
            });

            assertEquals("ERROR UNKNOWN_TOKEN", exception.getMessage());
        }
    }
}