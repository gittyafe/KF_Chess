package org.example.network.server.room;

/**
 * Immutable record of a connected session's identity and assigned seat
 * color within whatever room it's currently in.
 */
public record PlayerInfo(String username, char color) {}
