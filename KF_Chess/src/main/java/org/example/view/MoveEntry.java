package org.example.view;

public class MoveEntry {
    private final String time;
    private final String moveNotation;

    public MoveEntry(String time, String moveNotation) {
        this.time = time;
        this.moveNotation = moveNotation;
    }

    public String getTimeString() {
        return time;
    }

    public String getMoveNotation() {
        return moveNotation;
    }
}