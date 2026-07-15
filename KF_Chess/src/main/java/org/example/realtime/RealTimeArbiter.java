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
    private List<MovingPiece> activeMotion = new ArrayList<>();
    private List<MovingPiece> activeJump = new ArrayList<>();

    private static final int MS_PER_CELL = 1000;

    public void setActiveMotion(int distanceCells, Piece piece, Position destination) {
        piece.setState(State.MOVING);
        this.activeMotion.add(new MovingPiece(piece, destination, distanceCells * MS_PER_CELL, false));
    }

    public void setActiveJump(Piece piece) {
        piece.setState(State.JUMPING);
        this.activeJump.add(new MovingPiece(piece, piece.getSquare(), MS_PER_CELL, true));
    }

    public boolean hasActiveMotion() {
        return !this.activeMotion.isEmpty();
    }

    public boolean hasActiveJump() {
        return !this.activeJump.isEmpty();
    }

    public List<MovingPiece> getActiveMotion() {
        return new ArrayList<>(this.activeMotion);
    }
    public List<MovingPiece> getActiveJump() {
        return new ArrayList<>(this.activeJump);
    }

    public MovingPiece getMovingPiece(int pieceId) {
        for (MovingPiece motion : activeMotion) {
            if (motion.getPiece().getId() == pieceId) {
                return motion;
            }
        }
        for (MovingPiece jump : activeJump) {
            if (jump.getPiece().getId() == pieceId) {
                return jump;
            }
        }
        return null;
    }

        /**
         * Advance both motion and jump timers and return pieces that finished moving.
         *
         * @param deltaTime time in milliseconds to advance
         * @return list of completed moving pieces
         */
    public List<MovingPiece> updateTime(long deltaTime) {
        List<MovingPiece> finishedThisTick = new ArrayList<>();

        for (int i = 0; i < activeMotion.size(); i++) {
            MovingPiece motion = activeMotion.get(i);
            motion.decrementTimeLeft(deltaTime);
            if (motion.isTimeUp()) {
                finishedThisTick.add(motion);
                activeMotion.remove(i);
                i--;
            }
        }

        for (int i = 0; i < activeJump.size(); i++) {
            MovingPiece jump = activeJump.get(i);
            jump.decrementTimeLeft(deltaTime);
            if (jump.isTimeUp()) {
                finishedThisTick.add(jump);
                activeJump.remove(i);
                i--;
            }
        }

        return finishedThisTick;
    }
}