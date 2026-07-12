package models;

public class MovingPiece {
    int timeLeft;
    Piece piece;
    Position destination;

    public MovingPiece(Piece piece, Position destination, int timeLeft) {
        this.piece = piece;
        this.destination = destination;
        this.timeLeft = timeLeft;
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