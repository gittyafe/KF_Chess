package org.example.models;

public class Piece {
    private int id;
    private char color;
    private char type;
    private Position square;
    private State state;


    public Piece(int id, char color, char type, Position square) {
        this(id, color, type, square, State.IDLE);
    }

    public Piece(int id, char color, char type, Position square, State state) {
        this.id = id;
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

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    /**
     * Promote this piece to a new type.
     *
     * @param newType the type to promote the piece to
     */
    public void promote(char newType) {
        this.type = newType;
    }
}