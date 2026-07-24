package org.example.network.server.room;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tracks who is connected to a single room: the two seated players (white
 * and black) plus any spectators, and resolves join/reconnect requests into
 * a seat. Pure "who is here and what seat are they in" state -- no
 * networking, no scheduling, no game rules.
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
            if (whiteSession != null) sessions.remove(whiteSession);
            whiteSession = newSession;
        } else {
            if (blackSession != null) sessions.remove(blackSession);
            blackSession = newSession;
        }
        sessions.add(newSession);
        return true;
    }

    public void removeSession(WebSocketSession session) {
        sessions.remove(session);
    }

    public boolean isRoomActive() {
        return (whiteSession != null && whiteSession.isOpen()) || (blackSession != null && blackSession.isOpen());
    }

    public char getColorForUsername(String username) {
        if (username.equalsIgnoreCase(whiteUsername)) return 'W';
        if (username.equalsIgnoreCase(blackUsername)) return 'B';
        return '-';
    }

    public String usernameFor(WebSocketSession session) {
        if (session == whiteSession) return whiteUsername;
        if (session == blackSession) return blackUsername;
        return null;
    }

    public String opponentUsernameFor(WebSocketSession session) {
        if (session == whiteSession) return blackUsername;
        if (session == blackSession) return whiteUsername;
        return null;
    }

    public boolean isStarted() { return started; }
    public String getWhiteUsername() { return whiteUsername; }
    public String getBlackUsername() { return blackUsername; }
    public List<WebSocketSession> getSessions() { return sessions; }
}
