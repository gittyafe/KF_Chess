package network.server;

import org.example.network.server.room.GameRoom;
import org.example.network.server.room.RoomPlayers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoomPlayersTest {

    private RoomPlayers players;
    private WebSocketSession whiteSession;
    private WebSocketSession blackSession;
    private WebSocketSession spectatorSession;

    @BeforeEach
    void setUp() {
        players = new RoomPlayers();
        whiteSession = mock(WebSocketSession.class);
        blackSession = mock(WebSocketSession.class);
        spectatorSession = mock(WebSocketSession.class);
    }

    @Test
    void firstPlayer_becomesWhite_andRoomNotStarted() {
        GameRoom.JoinResult result = players.addPlayer(whiteSession, "alice", "room1");

        assertEquals(GameRoom.JoinRole.WHITE, result.role());
        assertEquals('W', result.color());
        assertFalse(players.isStarted());
        assertEquals("alice", players.getWhiteUsername());
        assertTrue(players.getSessions().contains(whiteSession));
    }

    @Test
    void secondPlayer_becomesBlack_andRoomStarts() {
        players.addPlayer(whiteSession, "alice", "room1");
        GameRoom.JoinResult result = players.addPlayer(blackSession, "bob", "room1");

        assertEquals(GameRoom.JoinRole.BLACK, result.role());
        assertEquals('B', result.color());
        assertTrue(players.isStarted());
        assertEquals("bob", players.getBlackUsername());
    }

    @Test
    void thirdPlayer_becomesSpectator() {
        players.addPlayer(whiteSession, "alice", "room1");
        players.addPlayer(blackSession, "bob", "room1");
        GameRoom.JoinResult result = players.addPlayer(spectatorSession, "carol", "room1");

        assertEquals(GameRoom.JoinRole.SPECTATOR, result.role());
        assertEquals('-', result.color());
        assertTrue(players.isSpectator(spectatorSession));
    }

    @Test
    void reconnect_replacesWhiteSession_caseInsensitiveUsername() {
        players.addPlayer(whiteSession, "alice", "room1");
        WebSocketSession newSession = mock(WebSocketSession.class);

        boolean rebound = players.reconnect(newSession, "ALICE");

        assertTrue(rebound);
        assertFalse(players.getSessions().contains(whiteSession));
        assertTrue(players.getSessions().contains(newSession));
        assertEquals("alice", players.usernameFor(newSession));
    }

    @Test
    void reconnect_replacesBlackSession() {
        players.addPlayer(whiteSession, "alice", "room1");
        players.addPlayer(blackSession, "bob", "room1");
        WebSocketSession newSession = mock(WebSocketSession.class);

        boolean rebound = players.reconnect(newSession, "bob");

        assertTrue(rebound);
        assertEquals("bob", players.usernameFor(newSession));
        assertFalse(players.getSessions().contains(blackSession));
    }

    @Test
    void reconnect_unknownUsername_returnsFalse() {
        players.addPlayer(whiteSession, "alice", "room1");

        boolean rebound = players.reconnect(mock(WebSocketSession.class), "nobody");

        assertFalse(rebound);
    }

    @Test
    void isRoomActive_trueIfEitherSeatOpen() {
        players.addPlayer(whiteSession, "alice", "room1");
        players.addPlayer(blackSession, "bob", "room1");
        when(whiteSession.isOpen()).thenReturn(false);
        when(blackSession.isOpen()).thenReturn(true);

        assertTrue(players.isRoomActive());
    }

    @Test
    void isRoomActive_falseIfNeitherSeatOpen() {
        players.addPlayer(whiteSession, "alice", "room1");
        players.addPlayer(blackSession, "bob", "room1");
        when(whiteSession.isOpen()).thenReturn(false);
        when(blackSession.isOpen()).thenReturn(false);

        assertFalse(players.isRoomActive());
    }

    @Test
    void getColorForUsername_matchesSeatedPlayers() {
        players.addPlayer(whiteSession, "alice", "room1");
        players.addPlayer(blackSession, "bob", "room1");

        assertEquals('W', players.getColorForUsername("Alice"));
        assertEquals('B', players.getColorForUsername("BOB"));
        assertEquals('-', players.getColorForUsername("nobody"));
    }

    @Test
    void opponentUsernameFor_returnsOtherSeat() {
        players.addPlayer(whiteSession, "alice", "room1");
        players.addPlayer(blackSession, "bob", "room1");

        assertEquals("bob", players.opponentUsernameFor(whiteSession));
        assertEquals("alice", players.opponentUsernameFor(blackSession));
        assertNull(players.opponentUsernameFor(spectatorSession));
    }

    @Test
    void isSpectator_falseForSeatedPlayers_trueForOthers() {
        players.addPlayer(whiteSession, "alice", "room1");
        players.addPlayer(blackSession, "bob", "room1");

        assertFalse(players.isSpectator(whiteSession));
        assertFalse(players.isSpectator(blackSession));
        assertTrue(players.isSpectator(spectatorSession));
        assertFalse(players.isSpectator(null));
    }

    @Test
    void removeSession_dropsFromSessionsList() {
        players.addPlayer(whiteSession, "alice", "room1");
        players.removeSession(whiteSession);

        assertFalse(players.getSessions().contains(whiteSession));
    }
}
