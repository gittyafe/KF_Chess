package models;

import rules.IPieceRule;
import rules.RuleEngine;

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
    private IPieceRule rule;

    public Piece(int id, char color, char type, Position square, IPieceRule rule) {
        this(id, color, type, square, STATE.IDLE, rule);
    }

    public Piece(int id, char color, char type, Position square, STATE state, IPieceRule rule) {
        this.id = id;
        this.color = color;
        this.type = type;
        this.square = square;
        this.state = state;
        this.rule = rule;
    }

    public IPieceRule getPieceRule() {
        return rule;
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

    public Position getPosition() {
        return square;
    }

    public void setPosition(Position position) {
        this.square = position;
    }

}