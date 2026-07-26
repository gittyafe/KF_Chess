package network.server;

import org.example.network.server.room.GameRoom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * GameRoom builds a real GameEngine/Board/RealTimeArbiter/RatingService
 * internally rather than accepting them via injection, so these tests run
 * against the real engine loaded from board.csv on the classpath. They
 * verify sequencing/lifecycle behavior (who gets notified when, idempotent
 * endGame, cleanup callback) rather than chess rules themselves.
 */
class GameRoomTest {

    private GameRoom room;
    private WebSocketSession white;
    private WebSocketSession black;

    @BeforeEach
    void setUp() {
        room = new GameRoom("room1");
        white = mock(WebSocketSession.class);
        black = mock(WebSocketSession.class);
        when(white.isOpen()).thenReturn(true);
        when(black.isOpen()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        room.shutdown();
    }

    @Test
    void addPlayer_first_isWhite_gameNotStartedYet() {
        GameRoom.JoinResult result = room.addPlayer(white, "alice");

        assertEquals(GameRoom.JoinRole.WHITE, result.role());
        assertFalse(room.isStarted());
        assertEquals("alice", room.getWhiteUsername());
    }

    @Test
    void addPlayer_second_isBlack_andStartsGame() throws IOException {
        room.addPlayer(white, "alice");
        GameRoom.JoinResult result = room.addPlayer(black, "bob");

        assertEquals(GameRoom.JoinRole.BLACK, result.role());
        assertTrue(room.isStarted());
        assertEquals("bob", room.getBlackUsername());
        // Both seats should have received a GAME_STARTED broadcast.
        verify(white, atLeastOnce()).sendMessage(any(TextMessage.class));
        verify(black, atLeastOnce()).sendMessage(any(TextMessage.class));
    }

    @Test
    void addPlayer_third_isSpectator() {
        room.addPlayer(white, "alice");
        room.addPlayer(black, "bob");
        WebSocketSession spectator = mock(WebSocketSession.class);
        when(spectator.isOpen()).thenReturn(true);

        GameRoom.JoinResult result = room.addPlayer(spectator, "carol");

        assertEquals(GameRoom.JoinRole.SPECTATOR, result.role());
        assertTrue(room.isSpectator(spectator));
    }

    @Test
    void getColorForUsername_reflectsSeating() {
        room.addPlayer(white, "alice");
        room.addPlayer(black, "bob");

        assertEquals('W', room.getColorForUsername("alice"));
        assertEquals('B', room.getColorForUsername("bob"));
    }

    @Test
    void endGame_isIdempotent_onlyBroadcastsAndCallsBackOnce() {
        room.addPlayer(white, "alice");
        room.addPlayer(black, "bob");
        Runnable callback = mock(Runnable.class);
        room.setOnEnded(callback);

        room.endGame("alice", "bob", "CHECKMATE");
        room.endGame("alice", "bob", "CHECKMATE"); // second call should be a no-op

        verify(callback, times(1)).run();
        assertTrue(room.isEnded());
    }

    @Test
    void endGame_withNullWinnerLoser_doesNotThrow_stillBroadcastsGameOver() {
        room.addPlayer(white, "alice");
        room.addPlayer(black, "bob");

        assertDoesNotThrow(() -> room.endGame(null, null, "DRAW"));
        assertTrue(room.isEnded());
    }

    @Test
    void reconnectPlayer_unknownUsername_returnsFalse() {
        room.addPlayer(white, "alice");
        room.addPlayer(black, "bob");
        WebSocketSession newSession = mock(WebSocketSession.class);

        assertFalse(room.reconnectPlayer(newSession, "nobody"));
    }

    @Test
    void reconnectPlayer_validParticipant_reboundsAndSendsState() {
        room.addPlayer(white, "alice");
        room.addPlayer(black, "bob");
        WebSocketSession newSession = mock(WebSocketSession.class);
        when(newSession.isOpen()).thenReturn(true);

        boolean rebound = room.reconnectPlayer(newSession, "alice");

        assertTrue(rebound);
        assertEquals('W', room.getColorForUsername("alice"));
    }

    @Test
    void reconnectPlayer_afterGameEnded_returnsFalse() {
        room.addPlayer(white, "alice");
        room.addPlayer(black, "bob");
        room.endGame("alice", "bob", "CHECKMATE");

        assertFalse(room.reconnectPlayer(mock(WebSocketSession.class), "alice"));
    }

    @Test
    void handlePlayerDisconnect_beforeGameStarted_isNoOp() {
        room.addPlayer(white, "alice"); // only white seated, not started
        Runnable callback = mock(Runnable.class);
        room.setOnEnded(callback);

        assertDoesNotThrow(() -> room.handlePlayerDisconnect(white));
        verify(callback, never()).run();
    }

    @Test
    void removeSession_dropsFromSessionsList() {
        room.addPlayer(white, "alice");

        room.removeSession(white);

        assertFalse(room.getSessions().contains(white));
    }

    @Test
    void onEndedCallback_exceptionDuringCallback_doesNotPropagate() {
        room.addPlayer(white, "alice");
        room.addPlayer(black, "bob");
        room.setOnEnded(() -> { throw new RuntimeException("callback boom"); });

        assertDoesNotThrow(() -> room.endGame("alice", "bob", "CHECKMATE"));
    }
}
