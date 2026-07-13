package models;

import org.example.models.MovingPiece;
import org.example.models.Piece;
import org.example.models.Position;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MovingPieceTest {

    @Test
    @DisplayName("Should correctly decrement time left and recognize when time is up")
    void decrementTimeLeft_VariousDeltaTimes_UpdatesCorrectly() {
        // Arrange
        Piece piece = new Piece(1, 'W', 'P', new Position(0, 0));
        Position dest = new Position(0, 2);
        MovingPiece movingPiece = new MovingPiece(piece, dest, 100, false);

        // Act & Assert 1: הפחתה חלקית
        movingPiece.decrementTimeLeft(40);
        assertEquals(60, movingPiece.getTimeLeft());
        assertFalse(movingPiece.isTimeUp());

        // Act & Assert 2: הגעה בדיוק ל-0
        movingPiece.decrementTimeLeft(60);
        assertEquals(0, movingPiece.getTimeLeft());
        assertTrue(movingPiece.isTimeUp());

        // Act & Assert 3: ירידה אל מתחת ל-0
        movingPiece.decrementTimeLeft(10);
        assertTrue(movingPiece.isTimeUp());
    }
}