package org.example.network;

/**
 * Deliberately tiny: a username and the color this session was assigned at
 * JOIN time. Rating, passwords, and persistence belong to the later
 * SQLite + ELO slide - keeping this minimal now means that slide can extend
 * it without unwinding anything here.
 */
public class PlayerInfo {
    private final String username;
    private final char color; // 'W' or 'B'

    public PlayerInfo(String username, char color) {
        this.username = username;
        this.color = color;
    }

    public String getUsername() {
        return username;
    }

    public char getColor() {
        return color;
    }
}
