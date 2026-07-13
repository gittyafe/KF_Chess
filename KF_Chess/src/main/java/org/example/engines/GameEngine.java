package org.example.engines;

import java.util.List;

import org.example.models.Board;
import org.example.models.MoveRequest;
import org.example.models.MoveStatus;
import org.example.models.MovingPiece;
import org.example.models.Piece;
import org.example.models.Position;
import org.example.models.State;
import org.example.rules.RuleEngine;

/**
 * Main game engine that orchestrates board state and game flow
 */
public class GameEngine {
    private Board board;
    private boolean isGameOver = false;
    private RealTimeEngine rta;

    public GameEngine(Board board, RealTimeEngine rta) {
        this.board = board;
        this.rta = rta;
    }

    public boolean isGameOver() {
        return isGameOver;
    }

    public Piece getPieceAt(Position position) {
        return board.queryPieceAt(position);
    }

    /**
     * Request a move for a piece from one position to another.
     *
     * @param from source position
     * @param to   destination position
     * @return move request result with validation status
     */
    public MoveRequest requestMove(Position from, Position to) {
        Piece piece = board.queryPieceAt(from);

        MoveRequest moveResult = validateMove(piece, from, to);
        if (moveResult.isValid()) {
            setPieceMoving(piece, from, to);
        }
        return moveResult;
    }

    /**
     * Mark a piece as moving and schedule its animated motion.
     *
     * @param piece the piece to move
     * @param from  source position
     * @param to    destination position
     */
    public void setPieceMoving(Piece piece, Position from, Position to) {
        if (piece == null || from == null || to == null) {
            return;
        }
        int distanceCells = Math.max(Math.abs(to.getRow() - from.getRow()),
                Math.abs(to.getColumn() - from.getColumn()));
        rta.setActiveMotion(distanceCells, piece, to);
    }

    /**
     * Validate whether a move is allowed in the current board and game state.
     *
     * @param piece the moving piece
     * @param from  source position
     * @param to    destination position
     * @return move request result indicating whether the move is allowed
     */
    public MoveRequest validateMove(Piece piece, Position from, Position to) {
        if (isGameOver)
            return new MoveRequest(MoveStatus.GAME_OVER, false);

        if (rta.hasActiveMotion())
            return new MoveRequest(MoveStatus.ANOTHER_PIECE_MOVING, false);

        if (piece != null && piece.getState() != State.IDLE)
            return new MoveRequest(MoveStatus.PIECE_ALREADY_IN_MOTION, false);

        if (to != null && (to.getRow() < 0 || to.getColumn() < 0 || to.getRow() >= board.getHeight()
                || to.getColumn() >= board.getWidth())) {
            return new MoveRequest(MoveStatus.OUT_OF_BOUNDS, false);
        }

        if (from != null && (from.getRow() < 0 || from.getColumn() < 0 || from.getRow() >= board.getHeight()
                || from.getColumn() >= board.getWidth())) {
            return new MoveRequest(MoveStatus.OUT_OF_BOUNDS, false);
        }

        Piece targetPiece = board.queryPieceAt(to);
        if (targetPiece != null && targetPiece.getColor() == piece.getColor()) {
            return new MoveRequest(MoveStatus.SAME_COLOR_OCCUPIED, false);
        }

        if (!RuleEngine.isValidMove(piece, from, to, board))
            return new MoveRequest(MoveStatus.INVALID_MOVE, false);

        return new MoveRequest(MoveStatus.SUCCESS, true);
    }

    /**
     * Advance the real-time engine by the specified interval and finalize any
     * completed moves.
     *
     * @param ms number of milliseconds to advance
     */
    public void wait_(long ms) {
        List<MovingPiece> finishedThisTick = rta.updateTime(ms);

        for (MovingPiece finished : finishedThisTick) {
            if (!finished.isJump()) {
                arrivedPiece(finished);
            }
        }
        for (MovingPiece finished : finishedThisTick) {
            if (finished.isJump()) {
                arrivedPiece(finished);
            }
        }
    }

    public void arrivedPiece(MovingPiece finished) {
        Piece piece = finished.getPiece();
        Position destination = finished.getDestination();

        Piece targetPiece = board.queryPieceAt(destination);

        if (targetPiece != null && targetPiece.getState() != State.JUMPING) {
            board.removePiece(targetPiece);
            if (targetPiece.getType() == 'K') {
                isGameOver = true;
            }
        }
        piece.setState(State.IDLE);
        board.movePiece(piece, destination);
        //המובפיס לא שינה את הדסטיניישן של הפיס!!
        promoteIfNeeded(piece);
    }

    /**
     * Request a jump action for the piece at the specified destination.
     *
     * @param destination target position for jump selection
     * @return move status result
     */
    public MoveStatus jumpRequest(Position destination) {
        if (isGameOver)
            return MoveStatus.GAME_OVER;
        if (!board.isInsideBounds(destination))
            return MoveStatus.OUT_OF_BOUNDS;

        Piece piece = board.queryPieceAt(destination);
        if (piece == null)
            return MoveStatus.INVALID_MOVE;
        if (rta.hasActiveJump())
            return MoveStatus.ANOTHER_PIECE_MOVING;
        if (piece.getState() != State.IDLE)
            return MoveStatus.PIECE_ALREADY_IN_MOTION;

        rta.setActiveJump(piece);
        return MoveStatus.SUCCESS;
    }

    private void promoteIfNeeded(Piece piece) {
        if (piece.getType() == 'P') {
            int targetRow = piece.getSquare().getRow();

            int lastRowForWhite = 0;
            int lastRowForBlack = board.getHeight() - 1;

            if ((piece.getColor() == 'w' && targetRow == lastRowForWhite) ||
                    (piece.getColor() == 'b' && targetRow == lastRowForBlack)) {
                piece.promote('Q');
            }
        }
    }

}