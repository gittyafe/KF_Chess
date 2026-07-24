package org.example.network.client;

/**
 * The only two things {@link ServerMessageDispatcher} ever needs to do
 * *back* on {@link ChessWebSocketClient}, rather than just publishing to
 * the bus:
 *
 *  - a LOGIN_SUCCESS message means "remember we're logged in" (so a later
 *    dropped connection knows to auto-reconnect instead of sitting idle);
 *  - a MATCH_FOUND message means "immediately reply with JOIN_MATCH".
 *
 * Expressing that as a two-method interface (implemented as an anonymous
 * class where the socket client is built) keeps the dispatcher from
 * needing a full reference to ChessWebSocketClient, which would otherwise
 * create a circular dependency between "the thing that parses messages"
 * and "the thing that owns the socket".
 */
interface ServerMessageCallbacks {

    void onLoginSuccess();

    void requestJoinMatch(String roomId);
}
