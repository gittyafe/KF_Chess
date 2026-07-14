package org.example.realtime;

import java.util.ArrayList;
import java.util.List;
import org.example.models.MovingPiece;
import org.example.models.Piece;
import org.example.models.Position;
import org.example.models.State;

/**
 * Tracks active piece motions and jump timing.
 */
public class RealTimeArbiter {
    private MovingPiece activeMotion = null;
    private MovingPiece activeJump = null;

    private static final int MS_PER_CELL = 1000;

    public void setActiveMotion(int distanceCells, Piece piece, Position destination) {
        piece.setState(State.MOVING);
        this.activeMotion = new MovingPiece(piece, destination, distanceCells * MS_PER_CELL, false);
    }

    public void setActiveJump(Piece piece) {
        piece.setState(State.JUMPING);
        this.activeJump = new MovingPiece(piece, piece.getSquare(), MS_PER_CELL, true);
    }

    public boolean hasActiveMotion() {
        return this.activeMotion != null;
    }

    public boolean hasActiveJump() {
        return this.activeJump != null;
    }

    /**
     * Advance both motion and jump timers and return pieces that finished moving.
     *
     * @param deltaTime time in milliseconds to advance
     * @return list of completed moving pieces
     */
    public List<MovingPiece> updateTime(long deltaTime) {
        List<MovingPiece> finishedThisTick = new ArrayList<>();

        if (activeMotion != null) {
            activeMotion.decrementTimeLeft(deltaTime);
            if (activeMotion.isTimeUp()) {
                finishedThisTick.add(activeMotion);
                activeMotion = null;
            }
        }

        if (activeJump != null) {
            activeJump.decrementTimeLeft(deltaTime);
            if (activeJump.isTimeUp()) {
                finishedThisTick.add(activeJump);
                activeJump = null;
            }
        }

        return finishedThisTick;
    }
}