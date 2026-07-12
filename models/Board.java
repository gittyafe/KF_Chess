package models;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the chess board state.
 * Stores pieces and provides access methods for placement and movement.
 */
public class Board {
    private List<Piece> pieces;
    private int width;
    private int height;

    public Board() {
        this.width = 0;
        this.height = 0;
        this.pieces = new ArrayList<>();
    }

    public Board(int width, int height) {
        this.width = width;
        this.height = height;
        this.pieces = new ArrayList<>();
    }

    public boolean addPiece(Piece piece) {
        if (piece == null) {
            return false;
        }

        Position position = piece.getSquare();
        if (position == null || !isInsideBounds(position)) {
            return false;
        }

        if (queryPieceAt(position) != null) {
            return false;
        }

        if (!pieces.contains(piece)) {
            pieces.add(piece);
        }
        return true;
    }

    public boolean removePiece(Piece piece) {
        if (piece == null) {
            return false;
        }

        boolean removed = pieces.remove(piece);
        if (removed) {
            piece.setSquare(null);
            piece.setState(STATE.CUPTURED);
        }
        return removed;
    }

    public Piece queryPieceAt(Position position) {
        if (position == null || !isInsideBounds(position)) {
            return null;
        }

        for (Piece piece : pieces) {
            Position piecePosition = piece.getSquare();
            if (piecePosition != null && piecePosition.equals(position)
                    && (piece.getState() != STATE.CUPTURED)) {
                return piece;
            }
        }
        return null;
    }

    public Piece queryPieceAt(int row, int column) {
        return queryPieceAt(new Position(row, column));
    }

    public boolean movePiece(Piece piece, Position destination) {
        // ????
        if (piece == null || destination == null || !isInsideBounds(destination)) {
            return false;
        }

        if (!pieces.contains(piece)) {
            return false;
        }

        Position currentPosition = piece.getSquare();
        if (currentPosition != null && currentPosition.equals(destination)) {
            return true;
        }

        if (queryPieceAt(destination) != null && queryPieceAt(destination) != piece) {
            return false;
        }
        //// ????

        piece.setSquare(destination);
        return true;
    }

    public boolean isInsideBounds(Position position) {
        if (position == null) {
            return false;
        }
        return position.getRow() >= 0 && position.getRow() < height
                && position.getColumn() >= 0 && position.getColumn() < width;
    }

    public boolean isEmpty() {
        return pieces.isEmpty();
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public List<Piece> getPieces() {
        return pieces;
    }
}