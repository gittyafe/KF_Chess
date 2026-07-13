package rules;

import org.example.models.Position;
import org.example.rules.MoveGeometry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class MoveGeometryTest {

    private final int BOARD_HEIGHT = 8;

    @Nested
    @DisplayName("Rook (R) Geometry Tests")
    class RookTests {
        @ParameterizedTest
        @CsvSource({
                "4, 4, 4, 7, true",  // תנועה אופקית ימינה
                "4, 4, 4, 1, true",  // תנועה אופקית שמאלה
                "4, 4, 7, 4, true",  // תנועה אנכית למטה
                "4, 4, 1, 4, true",  // תנועה אנכית למעלה
                "4, 4, 5, 5, false"  // תנועה אלכסונית - לא חוקי לצריח
        })
        @DisplayName("Rook moves should only be horizontal or vertical")
        void isDirectionValid_RookGeometry(int srcR, int srcC, int destR, int destC, boolean expected) {
            assertTrue(runTest('R', srcR, srcC, destR, destC, 'w', false) == expected);
        }
    }

    @Nested
    @DisplayName("Bishop (B) Geometry Tests")
    class BishopTests {
        @ParameterizedTest
        @CsvSource({
                "4, 4, 6, 6, true",  // אלכסון ראשי מטה
                "4, 4, 2, 2, true",  // אלכסון ראשי מעלה
                "4, 4, 2, 6, true",  // אלכסון משני מעלה
                "4, 4, 4, 5, false"  // תנועה ישרה - לא חוקי לרץ
        })
        @DisplayName("Bishop moves should only be perfectly diagonal")
        void isDirectionValid_BishopGeometry(int srcR, int srcC, int destR, int destC, boolean expected) {
            assertEquals(expected, runTest('B', srcR, srcC, destR, destC, 'w', false));
        }
    }

    @Nested
    @DisplayName("Knight (N) Geometry Tests")
    class KnightTests {
        @ParameterizedTest
        @CsvSource({
                "4, 4, 6, 5, true",  // 2 מטה, 1 ימינה
                "4, 4, 5, 6, true",  // 1 מטה, 2 ימינה
                "4, 4, 2, 3, true",  // 2 מעלה, 1 שמאלה
                "4, 4, 4, 6, false", // 2 ימינה בלבד
                "4, 4, 5, 5, false"  // אלכסון 1x1
        })
        @DisplayName("Knight moves must strictly follow the L-shape (2x1 or 1x2)")
        void isDirectionValid_KnightGeometry(int srcR, int srcC, int destR, int destC, boolean expected) {
            assertEquals(expected, runTest('N', srcR, srcC, destR, destC, 'w', false));
        }
    }

    @Nested
    @DisplayName("Pawn (P) Geometry Tests")
    class PawnTests {

        @ParameterizedTest
        @CsvSource({
                "6, 4, 5, 4, w, false, true",  // לבן: צעד אחד קדימה (שורה 6 ל-5)
                "6, 4, 4, 4, w, false, true",  // לבן: שני צעדים משורת ההתחלה (שורה 6 ל-4)
                "5, 4, 3, 4, w, false, false", // לבן: ניסיון ל-2 צעדים שלא משורת ההתחלה
                "6, 4, 5, 5, w, true, true",   // לבן: הכאה באלכסון
                "6, 4, 5, 5, w, false, false", // לבן: תנועה באלכסון ללא הכאה
                "6, 4, 5, 4, w, true, false"   // לבן: ניסיון הכאה ישר קדימה (אסור לרגלי)
        })
        @DisplayName("White Pawn movement logic and edge cases")
        void isDirectionValid_WhitePawn(int srcR, int srcC, int destR, int destC, char color, boolean isCapture, boolean expected) {
            assertEquals(expected, runTest('P', srcR, srcC, destR, destC, color, isCapture));
        }

        @ParameterizedTest
        @CsvSource({
                "1, 4, 2, 4, b, false, true",  // שחור: צעד אחד קדימה (שורה 1 ל-2)
                "1, 4, 3, 4, b, false, true",  // שחור: שני צעדים משורת ההתחלה (שורה 1 ל-3)
                "2, 4, 4, 4, b, false, false", // שחור: שני צעדים לא משורת התחלה
                "1, 4, 2, 5, b, true, true"    // שחור: הכאה באלכסון קדימה
        })
        @DisplayName("Black Pawn movement logic and edge cases")
        void isDirectionValid_BlackPawn(int srcR, int srcC, int destR, int destC, char color, boolean isCapture, boolean expected) {
            assertEquals(expected, runTest('P', srcR, srcC, destR, destC, color, isCapture));
        }
    }

    // מתודות עזר פרטיות לפישוט הקריאה בטסטים
    private boolean runTest(char type, int srcR, int srcC, int destR, int destC, char color, boolean isCapture) {
        return MoveGeometry.isDirectionValid(type, new Position(srcR, srcC), new Position(destR, destC), color, isCapture, BOARD_HEIGHT);
    }
}