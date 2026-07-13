package controllers;

import org.example.controllers.BoardMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("בדיקות יחידה עבור ממיר הקואורדינטות - BoardMapper")
class BoardMapperTest {

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "50, 0",
            "99, 0",
            "100, 1",
            "150, 1",
            "299, 2"
    })
    @DisplayName("Should correctly map pixel coordinates to cell indices based on 100px intervals")
    void pixelToCell_ValidPixels_ReturnsCorrectCellIndex(int pixel, int expectedCell) {
        // Act
        int actualCell = BoardMapper.pixelToCell(pixel);

        // Assert
        assertEquals(expectedCell, actualCell,
                String.format("Pixel %d should map to cell %d", pixel, expectedCell));
    }
}