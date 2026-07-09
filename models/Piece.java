package models;

/**
 * Represents a chess piece on the board.
 * Composite object: combines color + type + position + state.
 */
public class Piece {
    private int id;
    private char color;
    private char type;
    private Position square;
    private STATE state;

    public Piece(char color, char type) {
        this(color, type, null, STATE.IDLE);
    }

    public Piece(char color, char type, Position square, STATE state) {
        this.color = color;
        this.type = type;
        this.square = square;
        this.state = state;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public char getColor() {
        return color;
    }

    public void setColor(char color) {
        this.color = color;
    }

    public char getType() {
        return type;
    }

    public void setType(char type) {
        this.type = type;
    }

    public Position getSquare() {
        return square;
    }

    public void setSquare(Position square) {
        this.square = square;
    }

    public STATE getState() {
        return state;
    }

    public void setState(STATE state) {
        this.state = state;
    }

    public boolean isEmpty() {
        return color == ' ' && type == ' ';
    }
}