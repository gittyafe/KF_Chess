package org.example.engines;

import java.util.ArrayList;
import java.util.List;

import org.example.models.Board;
import org.example.models.MoveRequest;
import org.example.models.MoveStatus;
import org.example.models.MovingPiece;
import org.example.models.Piece;
import org.example.models.Position;
import org.example.models.State;
import org.example.realtime.MotionValidity;
import org.example.realtime.RealTimeArbiter;
import org.example.view.MoveListener;

/**
 * Main game engine that orchestrates board state and game flow
 */
public class GameEngine {
    private Board board;
    private boolean isGameOver = false;
    private RealTimeArbiter rta;
    private MotionValidity motion = new MotionValidity();

    public GameEngine(Board board, RealTimeArbiter rta) {
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

        if (!motion.isValidMove(piece, from, to, board))
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
        if(piece.getState() == State.JUMPING){
            piece.setState(State.SHORT_REST);
            rta.setShortRest(piece);
        } else if(piece.getState() == State.MOVING){
            String time = "00:" + String.format("%.3f", System.currentTimeMillis() % 60000 / 1000.0); // דוגמה לפורמט זמן
            notifyMoveListeners(time,piece.getSquare().toString(), piece.getColor());
            rta.setLongRest(piece);
            piece.setState(State.LONG_REST);
        }
        else {
            piece.setState(State.IDLE);
        }
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
        if (piece.getType() == 'P' && piece.getState() == State.IDLE ) {
            System.out.println("Checking promotion for piece: " + piece.getId() + " at position: " + piece.getSquare());
            int targetRow = piece.getSquare().getRow();

            int lastRowForWhite = 0;
            int lastRowForBlack = board.getHeight() - 1;

            if ((piece.getColor() == 'w' && targetRow == lastRowForWhite) ||
                    (piece.getColor() == 'b' && targetRow == lastRowForBlack)) {
                piece.promote('Q');
            }
        }
    }

    // 1. רשימה שמחזיקה את כל מי שמאזין למהלכים
    private final List<MoveListener> moveListeners = new ArrayList<>();

    // 2. מתודה שמאפשרת ל-Main או ל-Controller לרשום מאזינים
    public void addMoveListener(MoveListener listener) {
        this.moveListeners.add(listener);
    }

    // 3. הפעלת המאזינים ברגע שמהלך מתרחש
    private void notifyMoveListeners(String time, String moveNotation, char color) {
        for (MoveListener listener : moveListeners) {
            listener.onMoveAdded(time, moveNotation, color);
        }
    }
}