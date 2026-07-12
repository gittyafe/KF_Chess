
import java.util.List;

import models.Board;
import models.MoveRequest;
import models.MoveStatus;
import models.MovingPiece;
import models.Piece;
import models.Position;
import models.STATE;
import rules.RuleEngine;

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

    public MoveRequest requestMove(Position from, Position to) {
        Piece piece = board.queryPieceAt(from);

        MoveRequest moveResult = validateMove(piece, from, to);
        if (moveResult.isValid()) {
            movePiece(piece, from, to);
        }
        return moveResult;
    }

    public void movePiece(Piece piece, Position from, Position to) {
        if (piece == null || from == null || to == null) {
            return;
        }
        piece.setState(STATE.MOVING);
        int distanceCells = Math.max(Math.abs(to.getRow() - from.getRow()),
                Math.abs(to.getColumn() - from.getColumn()));
        rta.setActiveMotion(distanceCells, piece, to);
    }

    public MoveRequest validateMove(Piece piece, Position from, Position to) {
        if (isGameOver) {
            return new MoveRequest(MoveStatus.GAME_OVER, false);
        }

        if (rta.hasActiveMotion()) {
            return new MoveRequest(MoveStatus.ANOTHER_PIECE_MOVING, false);
        }

        if (piece != null && piece.getState() != STATE.IDLE) {
            return new MoveRequest(MoveStatus.PIECE_ALREADY_IN_MOTION, false);
        }

        // Check if the target position is within the board bounds
        if (to != null && (to.getRow() < 0 || to.getColumn() < 0 || to.getRow() >= board.getHeight()
                || to.getColumn() >= board.getWidth())) {
            return new MoveRequest(MoveStatus.OUT_OF_BOUNDS, false);
        }

        // Check if the source position is within the board bounds
        if (from != null && (from.getRow() < 0 || from.getColumn() < 0 || from.getRow() >= board.getHeight()
                || from.getColumn() >= board.getWidth())) {
            return new MoveRequest(MoveStatus.OUT_OF_BOUNDS, false);
        }

        // Check if the target position is occupied by a piece of the same color
        Piece targetPiece = board.queryPieceAt(to);
        if (targetPiece != null && targetPiece.getColor() == piece.getColor()) {
            return new MoveRequest(MoveStatus.SAME_COLOR_OCCUPIED, false);
        }

        if (!RuleEngine.isValidMove(piece, from, to, board)) {
            return new MoveRequest(MoveStatus.INVALID_MOVE, false);
        }

        return new MoveRequest(MoveStatus.SUCCESS, true);
    }

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

        if (targetPiece != null && targetPiece.getState() != STATE.JUMPING) {
            board.removePiece(targetPiece);
            if (targetPiece.getType() == 'K') {
                isGameOver = true;
            }
        }
        piece.setState(STATE.IDLE);
        piece.setPosition(destination);
        promoteIfNeeded(piece);
    }

    ////////////////////////////////////////////////////////////
    // לסדר את זה שיחזיר את הסטטוס של המהלך במקום בוליאןJumpRequest
    public void jumpRequest(Position destination) {
        Piece piece = board.queryPieceAt(destination);

        if (piece != null && !isGameOver && !rta.hasActiveJump() && piece.getState() == STATE.IDLE) {
            piece.setState(STATE.JUMPING);
            rta.setActiveJump(piece);
        }
    }

    private void promoteIfNeeded(Piece piece) {
        if (piece.getType() == 'P') {
            int targetRow = piece.getPosition().getRow();

            int lastRowForWhite = 0;
            int lastRowForBlack = board.getHeight() - 1;

            if ((piece.getColor() == 'w' && targetRow == lastRowForWhite) ||
                    (piece.getColor() == 'b' && targetRow == lastRowForBlack)) { // Simple check
                piece.promote('Q'); // Promote to Queen
            }
        }
    }

}