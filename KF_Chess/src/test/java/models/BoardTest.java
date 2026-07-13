package org.example.models;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class BoardTest {

    private Board board;
    private final int BOARD_WIDTH = 8;
    private final int BOARD_HEIGHT = 8;

    @BeforeEach
    void setUp() {
        board = new Board(BOARD_WIDTH, BOARD_HEIGHT);
    }

    @Nested
    @DisplayName("Bounds and Initialization Tests")
    class InitializationTests {

        @Test
        @DisplayName("New board should be empty and retain dimensions")
        void board_Initialization_ShouldBeEmpty() {
            assertTrue(board.isEmpty());
            assertEquals(BOARD_WIDTH, board.getWidth());
            assertEquals(BOARD_HEIGHT, board.getHeight());
        }

        @ParameterizedTest
        @CsvSource({
                "0, 0, true",
                "7, 7, true",
                "0, 7, true",
                "7, 0, true",
                "-1, 4, false",
                "4, -1, false",
                "8, 4, false",
                "4, 8, false"
        })
        @DisplayName("IsInsideBounds should accurately validate positions")
        void isInsideBounds_Coordinates_ValidatesCorrectly(int row, int col, boolean expected) {
            Position position = new Position(row, col);
            assertEquals(expected, board.isInsideBounds(position));
        }

        @Test
        @DisplayName("IsInsideBounds should return false for null position")
        void isInsideBounds_NullPosition_ReturnsFalse() {
            assertFalse(board.isInsideBounds(null));
        }
    }

    @Nested
    @DisplayName("Add Piece Scenarios")
    class AddPieceTests {

        @Test
        @DisplayName("Should successfully add a valid piece to an empty valid cell")
        void addPiece_ValidPieceAndCell_ReturnsTrue() {
            // Arrange
            Piece piece = new Piece(1, 'W', 'P', new Position(3, 3));

            // Act
            boolean result = board.addPiece(piece);

            // Assert
            assertTrue(result);
            assertFalse(board.isEmpty());
            assertEquals(piece, board.queryPieceAt(3, 3));
        }

        @Test
        @DisplayName("Should fail when adding a null piece")
        void addPiece_NullPiece_ReturnsFalse() {
            assertFalse(board.addPiece(null));
        }

        @Test
        @DisplayName("Should fail when adding a piece with out of bounds position")
        void addPiece_OutOfBounds_ReturnsFalse() {
            Piece piece = new Piece(1, 'W', 'P', new Position(10, 10));
            assertFalse(board.addPiece(piece));
        }

        @Test
        @DisplayName("Should fail when adding a piece to an already occupied cell")
        void addPiece_CellAlreadyOccupied_ReturnsFalse() {
            // Arrange
            Piece firstPiece = new Piece(1, 'W', 'P', new Position(2, 2));
            Piece secondPiece = new Piece(2, 'B', 'N', new Position(2, 2));
            board.addPiece(firstPiece);

            // Act
            boolean result = board.addPiece(secondPiece);

            // Assert
            assertFalse(result);
            assertEquals(firstPiece, board.queryPieceAt(2, 2));
        }

        @Test
        @DisplayName("Should not duplicate piece in list if added twice")
        void addPiece_DuplicatePiece_DoesNotDuplicate() {
            Piece piece = new Piece(1, 'W', 'P', new Position(2, 2));

            board.addPiece(piece);
            boolean secondAdd = board.addPiece(piece);

            assertFalse(secondAdd);
            assertEquals(1, board.getPieces().size());
        }
    }

    @Nested
    @DisplayName("Remove Piece Scenarios")
    class RemovePieceTests {

        @Test
        @DisplayName("Should remove existing piece and update its state to CAPTURED")
        void removePiece_ExistingPiece_RemovesAndUpdatesState() {
            // Arrange
            Piece piece = new Piece(1, 'W', 'P', new Position(4, 4));
            board.addPiece(piece);

            // Act
            boolean result = board.removePiece(piece);

            // Assert
            assertTrue(result);
            assertTrue(board.isEmpty());
            assertNull(piece.getSquare(), "Square should be set to null upon removal");
            assertEquals(State.CAPTURED, piece.getState(), "State should change to CAPTURED");
        }

        @Test
        @DisplayName("Should return false when attempting to remove a non-existent or null piece")
        void removePiece_NonExistentOrNull_ReturnsFalse() {
            Piece piece = new Piece(1, 'W', 'P', new Position(4, 4));

            assertFalse(board.removePiece(null));
            assertFalse(board.removePiece(piece));
        }
    }

    @Nested
    @DisplayName("Query Piece Scenarios")
    class QueryPieceTests {

        @Test
        @DisplayName("Should return null when querying a cell containing a CAPTURED piece")
        void queryPieceAt_CapturedPiece_ReturnsNull() {
            // Arrange
            Piece piece = new Piece(1, 'W', 'P', new Position(1, 1));
            board.addPiece(piece);
            board.removePiece(piece); // משנה את מצבו ל-CAPTURED ומנתק מהמשבצת

            // Act & Assert
            assertNull(board.queryPieceAt(1, 1));
        }
    }

    @Nested
    @DisplayName("Move Piece Scenarios")
    class MovePieceTests {

        @Test
        @DisplayName("Should successfully move a piece to a valid empty destination")
        void movePiece_ValidEmptyDestination_ReturnsTrueAndUpdatesPosition() {
            // Arrange
            Position startPos = new Position(0, 0);
            Position endPos = new Position(0, 5);
            Piece piece = new Piece(1, 'B', 'R', startPos);
            board.addPiece(piece);

            // Act
            boolean result = board.movePiece(piece, endPos);

            // Assert
            assertTrue(result);
            assertEquals(endPos, piece.getSquare());
            assertNull(board.queryPieceAt(startPos));
            assertEquals(piece, board.queryPieceAt(endPos));
        }

        @Test
        @DisplayName("Should return true immediately if piece is already at the destination")
        void movePiece_AlreadyAtDestination_ReturnsTrue() {
            Position pos = new Position(3, 3);
            Piece piece = new Piece(1, 'W', 'Q', pos);
            board.addPiece(piece);

            boolean result = board.movePiece(piece, pos);

            assertTrue(result);
        }

        @Test
        @DisplayName("Should fail when trying to move a piece to a cell occupied by another piece")
        void movePiece_DestinationOccupiedByAnother_ReturnsFalse() {
            // Arrange
            Piece rook = new Piece(1, 'W', 'R', new Position(0, 0));
            Piece knight = new Piece(2, 'W', 'N', new Position(0, 4));
            board.addPiece(rook);
            board.addPiece(knight);

            // Act
            boolean result = board.movePiece(rook, new Position(0, 4));

            // Assert
            assertFalse(result);
            assertEquals(new Position(0, 0), rook.getSquare(), "Rook should not have moved");
        }

        @Test
        @DisplayName("Should fail when destination is out of bounds or parameters are null")
        void movePiece_InvalidParametersOrBounds_ReturnsFalse() {
            Piece piece = new Piece(1, 'W', 'P', new Position(2, 2));
            board.addPiece(piece);

            assertFalse(board.movePiece(null, new Position(3, 3)));
            assertFalse(board.movePiece(piece, null));
            assertFalse(board.movePiece(piece, new Position(-1, 5)));
        }
    }
}