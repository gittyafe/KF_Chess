package org.example.bus;

/**
 * Canonical names for every event that flows through {@link GameEventBus}.
 *
 * Before this class existed, "JOIN_ACCEPTED", "BOARD_UPDATE_RECEIVED" etc.
 * were typed out fresh at every publish() and subscribe() call site, split
 * across the network package and the UI package. A typo in either place
 * (e.g. "BOARD_UPDATE_RECEIVED") would silently fail at runtime -- the
 * subscription just never fires, with no compiler error and no exception.
 *
 * Using these constants instead doesn't make the bus type-safe (it's still
 * String-keyed under the hood -- see the note in GameEventBus), but it does
 * mean a typo becomes a compile error, and "what events exist?" becomes a
 * one-file question instead of a grep across the whole codebase.
 */
public final class GameServerEvents {

    private GameServerEvents() {}

    // Gameplay
    public static final String BOARD_UPDATE_RECEIVED = "BOARD_UPDATE_RECEIVED";
    public static final String MOVE_LOGGED = "MOVE_LOGGED";
    public static final String PIECE_CAPTURED = "PIECE_CAPTURED";
    public static final String GAME_STARTED = "GAME_STARTED";
    public static final String GAME_OVER = "GAME_OVER";

    // Login / connection lifecycle
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String RECONNECT_ACCEPTED = "RECONNECT_ACCEPTED";
    public static final String RECONNECT_REJECTED = "RECONNECT_REJECTED";
    public static final String DISCONNECT_COUNTDOWN = "DISCONNECT_COUNTDOWN";
    public static final String DISCONNECT_CANCELLED = "DISCONNECT_CANCELLED";

    // Rooms & matchmaking
    public static final String JOIN_ACCEPTED = "JOIN_ACCEPTED";
    public static final String JOIN_REJECTED = "JOIN_REJECTED";
    public static final String CREATE_ACCEPTED = "CREATE_ACCEPTED";
    public static final String CREATE_REJECTED = "CREATE_REJECTED";
    public static final String MATCHMAKING_STARTED = "MATCHMAKING_STARTED";
    public static final String MATCHMAKING_TIMEOUT = "MATCHMAKING_TIMEOUT";
    public static final String MATCHMAKING_CANCELLED = "MATCHMAKING_CANCELLED";
    public static final String MATCH_FOUND = "MATCH_FOUND";

    // Rendering (UI bridge)
    public static final String FRAME_READY = "FRAME_READY";
}
