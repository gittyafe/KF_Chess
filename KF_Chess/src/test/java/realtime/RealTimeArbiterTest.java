package realtime;

import org.example.realtime.RealTimeArbiter;
import org.example.models.Piece;
import org.example.models.Position;
import org.example.models.MovingPiece;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RealTimeArbiterTest {

    private RealTimeArbiter rta;
    private Piece mockPiece;
    private Position dummyPosition;

    @BeforeEach
    void setUp() {
        rta = new RealTimeArbiter();
        dummyPosition = new Position(3, 3);
        mockPiece = new Piece(1, 'w', 'P', dummyPosition);
    }

    @Nested
    @DisplayName("Active Motion Lifecycle")
    class MotionTests {

        @Test
        @DisplayName("Setting active motion should activate state and calculate correct MS duration")
        void setActiveMotion_ValidParams_SetsCorrectStateAndTime() {
            // Arrange & Act
            rta.setActiveMotion(3, mockPiece, new Position(6, 3)); // מרחק של 3 תאים

            // Assert
            assertTrue(rta.hasActiveMotion());
            assertFalse(rta.hasActiveJump());

            // 3 תאים במכפיל של 1000ms = 3000ms. נקדם ב-2999 ונראה שזה עוד לא נגמר
            List<MovingPiece> finished = rta.updateTime(2999);
            assertTrue(finished.isEmpty());
            assertTrue(rta.hasActiveMotion());

            // עוד מילישניה אחת והתנועה מסתיימת
            finished = rta.updateTime(1);
            assertEquals(1, finished.size());
            assertFalse(finished.get(0).isJump());
            assertFalse(rta.hasActiveMotion());
        }
    }

    @Nested
    @DisplayName("Active Jump Lifecycle")
    class JumpTests {

        @Test
        @DisplayName("Setting active jump should activate state and expire exactly after 1000ms")
        void setActiveJump_ValidPiece_ExpiresAfterOneSecond() {
            // Arrange & Act
            rta.setActiveJump(mockPiece);

            // Assert
            assertTrue(rta.hasActiveJump());
            assertFalse(rta.hasActiveMotion());

            // טיק חלקי של 500ms
            List<MovingPiece> finished = rta.updateTime(500);
            assertTrue(finished.isEmpty());

            // סגירת ה-500ms הנותרים
            finished = rta.updateTime(500);
            assertEquals(1, finished.size());
            assertTrue(finished.get(0).isJump());
            assertFalse(rta.hasActiveJump());
        }
    }
}