package models;

public class MovingPiece {
    private int timeLeft;
    private Piece piece;
    private Position destination;
    private boolean isJump;


    public MovingPiece(Piece piece, Position destination, int timeLeft, boolean isJump) {
        this.piece = piece;
        this.destination = destination;
        this.timeLeft = timeLeft;
        this.isJump = isJump;
    }

    public boolean isJump() {
        return isJump;
    }

    public Piece getPiece() {
        return piece;
    }
    public Position getDestination() {
        return destination;
    }
    public int getTimeLeft() {
        return timeLeft;
    }
    public void decrementTimeLeft(long deltaTime) {
        this.timeLeft -= deltaTime;
    }
    public boolean isTimeUp() {
        return timeLeft <= 0;
    }
    public void setTimeLeft(int timeLeft) {
        this.timeLeft = timeLeft;
    }
}