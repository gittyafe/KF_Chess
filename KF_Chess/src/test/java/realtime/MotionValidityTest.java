package realtime;

import org.example.models.Board;
import org.example.models.Piece;
import org.example.models.Position;
import org.example.realtime.MotionValidity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class MotionValidityTest {

    @Mock
    private Board board;

    private MotionValidity motion;

    @BeforeEach
    void setUp() {
        // הגדרת גובה לוח דיפולטיבי עבור ה-Mock
        lenient().when(board.getHeight()).thenReturn(8);
        motion = new MotionValidity();
    }

    @Nested
    @DisplayName("Sliding Pieces Path Blocking Tests")
    class PathBlockingTests {

        @Test
        @DisplayName("Should return true for sliding piece when path is completely clear")
        void isValidMove_ClearPath_ReturnsTrue() {
            // Arrange - צריח לבן זז מ-(0,0) ל-(0,4)
            Piece rook = new Piece(1, 'w', 'R', new Position(0, 0));
            Position src = new Position(0, 0);
            Position dest = new Position(0, 4);

            // הגדרת החזרות lenient על מנת למנוע התנגשויות Strict Stubbing בין שתי החתימות
            lenient().when(board.queryPieceAt(anyInt(), anyInt())).thenReturn(null);
            lenient().when(board.queryPieceAt(any(Position.class))).thenReturn(null);

            // Act
            boolean result = motion.isValidMove(rook, src, dest, board);

            // Assert
            assertTrue(result);
        }

        @Test
        @DisplayName("Should return false when the path of a sliding piece is blocked by another piece")
        void isValidMove_BlockedPath_ReturnsFalse() {
            // 1. Arrange
            Piece slidingPiece = new Piece(1, 'w', 'R', new Position(0, 0));
            Position src = new Position(0, 0);
            Position dest = new Position(0, 4);

            // הכלי החוסם נמצא במיקום (0, 2)
            Piece blockingPiece = new Piece(2, 'b', 'P', new Position(0, 2));

            // תמיכה בשורה 13: מה יוחזר כשבודקים את משבצת היעד הסופית (0,4) - משבצת ריקה
            doReturn(null).when(board).queryPieceAt(any(Position.class));

            // תמיכה בתוך isPathClear: בדיקה לפי אינדקסים של שורה ועמודה
            doAnswer(invocation -> {
                int row = invocation.getArgument(0, Integer.class);
                int col = invocation.getArgument(1, Integer.class);

                // אם מנוע החוקים סורק את המשבצת החסומה (0,2), נחזיר את הכלי החוסם
                if (row == 0 && col == 2) {
                    return blockingPiece;
                }
                return null;
            }).when(board).queryPieceAt(anyInt(), anyInt());

            // 2. Act
            boolean result = motion.isValidMove(slidingPiece, src, dest, board);

            // 3. Assert
            assertFalse(result, "Should return false because the path is blocked at (0,2)");
        }
    } // <-- סוגר מסולסל מתוקן שסוגר את מחלקת PathBlockingTests כהלכה!

    @Nested
    @DisplayName("Pawn Contextual Obstacles Tests")
    class PawnObstacleTests {

        @Test
        @DisplayName("Pawn double step should fail if the intermediate square is blocked")
        void isValidMove_PawnDoubleStepBlocked_ReturnsFalse() {
            // Arrange - רגלי לבן בשורת ההתחלה (6,4) מנסה לקפוץ ל-(4,4), אך (5,4) חסום
            Piece pawn = new Piece(1, 'w', 'P', new Position(6, 4));
            Position src = new Position(6, 4);
            Position dest = new Position(4, 4);

            // הגדרת Mock גמיש לשורה 13 (משבצת היעד ריקה) ולסריקת האינטגרים
            lenient().when(board.queryPieceAt(dest)).thenReturn(null);
            lenient().when(board.queryPieceAt(any(Position.class))).thenReturn(null);
            lenient().when(board.queryPieceAt(5, 4)).thenReturn(new Piece(2, 'b', 'P', new Position(5, 4)));

            // Act
            boolean result = motion.isValidMove(pawn, src, dest, board);

            // Assert
            assertFalse(result, "Pawn double-step should fail if the skipped square contains a piece");
        }

        @Test
        @DisplayName("Pawn double step should succeed if the intermediate square is empty")
        void isValidMove_PawnDoubleStepClear_ReturnsTrue() {
            // Arrange - אותו תרחיש, אך המשבצת התיכונה ריקה
            Piece pawn = new Piece(1, 'w', 'P', new Position(6, 4));
            Position src = new Position(6, 4);
            Position dest = new Position(4, 4);

            lenient().when(board.queryPieceAt(dest)).thenReturn(null);
            lenient().when(board.queryPieceAt(any(Position.class))).thenReturn(null);
            lenient().when(board.queryPieceAt(anyInt(), anyInt())).thenReturn(null);

            // Act
            boolean result = motion.isValidMove(pawn, src, dest, board);

            // Assert
            assertTrue(result);
        }
    }

    @Nested
    @DisplayName("Invalid Geometry Safeguard")
    class GeometrySafeguardTests {

        @Test
        @DisplayName("Should immediately return false if the movement direction breaks chess geometry rules")
        void isValidMove_InvalidGeometry_ReturnsFalse() {
            // Arrange - רץ מנסה לזוז בצורה ישרה (לא חוקי גיאומטרית)
            Piece bishop = new Piece(1, 'w', 'B', new Position(0, 0));
            Position src = new Position(0, 0);
            Position dest = new Position(0, 5);

            // Act
            boolean result = motion.isValidMove(bishop, src, dest, board);

            // Assert
            assertFalse(result);
            // מוודאים שבגלל שהגיאומטריה נכשלה, המנוע בכלל לא טורח לבדוק חסימות במסלול (חסכון בריצה)
            verify(board, never()).queryPieceAt(anyInt(), anyInt());
        }
    }
}