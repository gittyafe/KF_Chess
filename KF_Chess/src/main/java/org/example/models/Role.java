package org.example.models;

public enum Role {
    WHITE("White Player ⚪"),
    BLACK("Black Player ⬛"),
    SPECTATOR("Spectator 👁️"),
    UNKNOWN("Waiting for role...");

    private final String displayName;

    Role(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Role fromChar(char colorChar) {
        return switch (Character.toUpperCase(colorChar)) {
            case 'W' -> WHITE;
            case 'B' -> BLACK;
            case 'S' -> SPECTATOR;
            default -> UNKNOWN;
        };
    }
}