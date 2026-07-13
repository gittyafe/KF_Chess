package engines;

import org.example.engines.GameEngine;
import org.example.engines.RealTimeEngine;
import org.example.models.*;
import org.example.rules.RuleEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameEngineTest {

    @Mock
    private Board board;

    @Mock
    private RealTimeEngine rta;

    private GameEngine gameEngine;

    @BeforeEach
    void setUp() {
        gameEngine = new GameEngine(board, rta);
    }

    @Nested
    @DisplayName("Move Request Validation Scenarios")
    class ValidationTests {

        @Test
        @DisplayName("Should block move validation if another piece is still animating")
        void validateMove_ActiveMotionExists_ReturnsAnotherPieceMovingStatus() {
            // Arrange
            when(rta.hasActiveMotion()).thenReturn(true);
            Piece piece = new Piece(1, 'w', 'P', new Position(1, 1));

            // Act
            MoveRequest response = gameEngine.validateMove(piece, new Position(1, 1), new Position(2, 1));

            // Assert
            assertFalse(response.isValid());
            assertEquals(MoveStatus.ANOTHER_PIECE_MOVING, response.getReason());
        }

        @Test
        @DisplayName("Should return OUT_OF_BOUNDS if source or destination positions are outside board dimensions")
        void validateMove_OutOfBoundsCoords_ReturnsOutOfBoundsStatus() {
            // Arrange - שימוש ב-lenient מונע מ-Mockito להכשיל את הטסט אם הקוד עוצר לפני הקריאה אליהם
            lenient().when(board.getWidth()).thenReturn(8);
            lenient().when(board.getHeight()).thenReturn(8);

            // במידה והמימוש הפנימי קורא ל-isInsideBounds, נגדיר לו להחזיר false עבור המיקום מחוץ לגבול
            lenient().when(board.isInsideBounds(argThat(pos -> pos != null && (pos.getRow() >= 8 || pos.getColumn() >= 8)))).thenReturn(false);

            Piece piece = new Piece(1, 'w', 'P', new Position(0, 0));

            // Act
            MoveRequest response = gameEngine.validateMove(piece, new Position(0, 0), new Position(8, 0));

            // Assert
            assertEquals(MoveStatus.OUT_OF_BOUNDS, response.getReason());
        }

        @Test
        @DisplayName("Should return SAME_COLOR_OCCUPIED if target cell contains a piece of the same team")
        void validateMove_TargetOccupiedByAlly_ReturnsSameColorOccupiedStatus() {
            // Arrange
            Position src = new Position(0, 0);
            Position dest = new Position(0, 1);
            Piece allyPiece = new Piece(1, 'w', 'R', src);
            Piece targetPiece = new Piece(2, 'w', 'P', dest);

            when(board.getWidth()).thenReturn(8);
            when(board.getHeight()).thenReturn(8);
            lenient().when(board.isInsideBounds(any(Position.class))).thenReturn(true);
            when(board.queryPieceAt(dest)).thenReturn(targetPiece);

            // Act
            MoveRequest response = gameEngine.validateMove(allyPiece, src, dest);

            // Assert
            assertEquals(MoveStatus.SAME_COLOR_OCCUPIED, response.getReason());
        }
    }

    @Nested
    @DisplayName("Piece Arrival & Game Context Modifiers")
    class ArrivalTests {

        @Test
        @DisplayName("When moving piece arrives at destination containing King, game should flag as over")
        void arrivedPiece_CapturingKing_TriggersGameOver() {
            // Arrange
            Position dest = new Position(4, 4);
            Piece attacker = new Piece(1, 'w', 'R', new Position(0, 4));
            Piece enemyKing = new Piece(2, 'b', 'K', dest);
            MovingPiece movingPiece = new MovingPiece(attacker, dest, 0, false);

            when(board.queryPieceAt(dest)).thenReturn(enemyKing);

            // Act
            gameEngine.arrivedPiece(movingPiece);

            // Assert
            verify(board).removePiece(enemyKing);
            verify(board).movePiece(attacker, dest);
            assertTrue(gameEngine.isGameOver());
            assertEquals(State.IDLE, attacker.getState());
        }

        @Test
        @DisplayName("White Pawn reaching index row 0 should promote to Queen")
        void arrivedPiece_PawnReachesEndRow_PromotesToQueen() {
            // Arrange
            Position dest = new Position(0, 5);
            Piece whitePawn = new Piece(1, 'w', 'P', dest);
            MovingPiece movingPiece = new MovingPiece(whitePawn, dest, 0, false);

            when(board.getHeight()).thenReturn(8);
            when(board.queryPieceAt(dest)).thenReturn(null);

            // Act
            gameEngine.arrivedPiece(movingPiece);

            // Assert
            assertEquals('Q', whitePawn.getType(), "Pawn should be promoted to Queen");
        }
    }

    @Nested
    @DisplayName("Jump Actions Logic")
    class JumpTests {

        @Test
        @DisplayName("Jump request on valid idle piece should update RealTimeEngine state")
        void jumpRequest_ValidIdlePiece_SucceedsAndDispatchesToRta() {
            // Arrange
            Position target = new Position(2, 2);
            Piece targetPiece = new Piece(5, 'b', 'N', target);

            when(board.isInsideBounds(target)).thenReturn(true);
            when(board.queryPieceAt(target)).thenReturn(targetPiece);
            when(rta.hasActiveJump()).thenReturn(false);

            // Act
            MoveStatus status = gameEngine.jumpRequest(target);

            // Assert
            assertEquals(MoveStatus.SUCCESS, status);
            verify(rta).setActiveJump(targetPiece);
        }
    }
}