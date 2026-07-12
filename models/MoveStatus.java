package models;

/**
 * MoveStatus
 */
public enum MoveStatus {
    SUCCESS,
    OUT_OF_BOUNDS,
    SAME_COLOR_OCCUPIED,
    INVALID_MOVE,
    PIECE_IN_MOTION,
    GAME_OVER
}
