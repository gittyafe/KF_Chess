package models;

public class MoveRequest {
    private MoveStatus reason;
    private boolean isValid;

    public MoveRequest(MoveStatus reason, boolean isValid) {
        this.reason = reason;
        this.isValid = isValid;
    }

    public MoveStatus getReason() {
        return reason;
    }

    public boolean isValid() {
        return isValid;
    }
}