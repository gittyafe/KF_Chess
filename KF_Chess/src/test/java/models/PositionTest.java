package models;

import org.example.models.Position;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PositionTest {

    @Test
    @DisplayName("Equals should return true for identical coordinates and false otherwise")
    void equals_ComparePositions_ReturnsCorrectComparison() {
        // Arrange
        Position pos1 = new Position(4, 5);
        Position pos2 = new Position(4, 5);
        Position differentRow = new Position(3, 5);
        Position differentCol = new Position(4, 6);

        // Assert
        assertTrue(pos1.equals(pos2), "Positions with same row and column should be equal");
        assertFalse(pos1.equals(differentRow), "Positions with different rows should not be equal");
        assertFalse(pos1.equals(differentCol), "Positions with different columns should not be equal");
    }
}