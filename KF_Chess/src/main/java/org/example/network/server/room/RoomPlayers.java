package org.example.network.server.room;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tracks who is connected to a single room: the two seated players (white
 * and black) plus any spectators, and resolves join/reconnect requests into
 * a seat. Pure "who is here and what seat are they in" state -- no
 * networking, no scheduling, no game rules.
 *
 * <p><b>What changed and why:</b> {@code usernameFor}, {@code
 * opponentUsernameFor} and {@code removeSession} used to compare sessions
 * with {@code ==} (or, for {@code isSpectator}, the default
 * {@code Object.equals()}, which for a plain object is the same thing as
 * {@code ==}). Both only ever return {@code true} for the literal same
 * Java object. That's fine in-process, but the moment a request for this
 * room arrives over NATS (from the Auth service or, via {@code
 * GameCommandNatsSubscriber}, from the Gateway), the {@code
 * WebSocketSession} handed to these methods is a freshly-constructed
 * {@code NatsWebSocketSessionAdapter} -- a different object every single
 * time, even for the exact same logical connection. Every one of those
 * comparisons was silently returning {@code false}: reconnect lookups,
 * disconnect handling ("who's the opponent of this session?"), and
 * spectator detection could all misfire for any session that arrived via
 * NATS. Fixed by comparing {@code session.getId()} (a stable string)
 * instead of object identity, everywhere a session is compared to a
 * previously-stored one.
 */
@Slf4j
public class RoomPlayers {

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    private WebSocketSession whiteSession;
    private String whiteUsername;
    private WebSocketSession blackSession;
    private String blackUsername;
    private volatile boolean started = false;

    public synchronized GameRoom.JoinResult addPlayer(WebSocketSession session, String username, String roomId) {
        if (whiteSession == null) {
            whiteSession = session;
            whiteUsername = username;
            sessions.add(session);
            log.info("[SERVER OUT] Player 1 (White) joined room [" + roomId + "]: " + username);
            return GameRoom.JoinResult.of(GameRoom.JoinRole.WHITE);
        }

        if (blackSession == null) {
            blackSession = session;
            blackUsername = username;
            sessions.add(session);
            started = true;
            log.info("[SERVER OUT] Player 2 (Black) joined room [" + roomId + "]: " + username);
            return GameRoom.JoinResult.of(GameRoom.JoinRole.BLACK);
        }

        sessions.add(session);
        log.info("[SERVER OUT] Spectator joined room [" + roomId + "]: " + username);
        return GameRoom.JoinResult.of(GameRoom.JoinRole.SPECTATOR);
    }

    /** Rebinds a fresh session onto an existing white/black seat. */
    public synchronized boolean reconnect(WebSocketSession newSession, String username) {
        boolean isWhite = username.equalsIgnoreCase(whiteUsername);
        boolean isBlack = !isWhite && username.equalsIgnoreCase(blackUsername);
        if (!isWhite && !isBlack) return false;

        if (isWhite) {
            removeSession(whiteSession); // by-id removal -- see removeSession() below
            whiteSession = newSession;
        } else {
            removeSession(blackSession);
            blackSession = newSession;
        }
        sessions.add(newSession);
        return true;
    }

    /**
     * Removes a session by id, not by object identity -- the argument here
     * is very often a different {@code WebSocketSession} instance than the
     * one actually stored in {@code sessions} (e.g. a NATS adapter rebuilt
     * for a disconnect notification that arrived from a different
     * process than the one that originally accepted the connection).
     * {@code List.remove(Object)} would silently no-op in that case,
     * leaking a stale entry in {@code sessions} forever.
     */
    public void removeSession(WebSocketSession session) {
        if (session == null) return;
        sessions.removeIf(s -> sameSession(s, session));
    }

    /**
     * Whether the room currently has a live seated player. Note: for a
     * session that only exists as a {@code NatsWebSocketSessionAdapter}
     * (i.e. this GameRoom does not live in the same process as the actual
     * client socket), {@code isOpen()} is hardcoded to always return
     * {@code true} -- it can't reflect real socket state. In the fully
     * distributed deployment, disconnects are detected and reported
     * explicitly via the PLAYER_DISCONNECT NATS event (see
     * {@code GameRoom.handleUserDisconnected}), not by polling
     * {@code isOpen()} here. Left as-is since it's exercised by the game
     * loop tick, which is intentionally out of scope for this pass --
     * flagging it so it isn't mistaken for reliable liveness detection in
     * the distributed case.
     */
    public boolean isRoomActive() {
        return (whiteSession != null && whiteSession.isOpen()) || (blackSession != null && blackSession.isOpen());
    }

    public char getColorForUsername(String username) {
        if (username.equalsIgnoreCase(whiteUsername)) return 'W';
        if (username.equalsIgnoreCase(blackUsername)) return 'B';
        return '-';
    }

    public String usernameFor(WebSocketSession session) {
        if (sameSession(session, whiteSession)) return whiteUsername;
        if (sameSession(session, blackSession)) return blackUsername;
        return null;
    }

    public String opponentUsernameFor(WebSocketSession session) {
        if (sameSession(session, whiteSession)) return blackUsername;
        if (sameSession(session, blackSession)) return whiteUsername;
        return null;
    }

    public boolean isSpectator(WebSocketSession session) {
        if (session == null) return false;
        return !sameSession(session, whiteSession) && !sameSession(session, blackSession);
    }

    /** Compares two sessions by id -- the only thing guaranteed to be stable across a NATS hop. */
    private boolean sameSession(WebSocketSession a, WebSocketSession b) {
        if (a == null || b == null) return false;
        return Objects.equals(a.getId(), b.getId());
    }

    public boolean isStarted() { return started; }
    public String getWhiteUsername() { return whiteUsername; }
    public String getBlackUsername() { return blackUsername; }
    public List<WebSocketSession> getSessions() { return sessions; }
}
