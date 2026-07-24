package org.example.network.server.room;

/**
 * Immutable record of a connected session's identity and assigned seat
 * color within whatever room it's currently in.
 *
 * NOTE: this file wasn't part of the uploaded sources -- it's reconstructed
 * from how it's used elsewhere (AuthHandler, MessageHandler,
 * ChessWebSocketHandler). If your real PlayerInfo has more fields, replace
 * this with the original.
 */
public record PlayerInfo(String username, char color) {}
