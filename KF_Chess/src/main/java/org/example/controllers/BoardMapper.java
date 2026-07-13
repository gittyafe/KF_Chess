package org.example.controllers;

/**
 * Maps pixel coordinates to board cells.
 */
public class BoardMapper {
    private static final int PIXELS_PER_CELL = 100;

    /**
     * Convert a pixel coordinate into a cell index.
     *
     * @param pixel pixel coordinate
     * @return board cell index
     */
    public static int pixelToCell(int pixel) {
        return pixel / PIXELS_PER_CELL;
    }

}
