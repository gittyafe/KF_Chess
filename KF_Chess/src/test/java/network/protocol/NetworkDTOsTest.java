package network.protocol;

import org.example.engines.GameSnapshot;
import org.example.network.protocol.NetworkDTOs.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * These DTOs are almost entirely "convenience constructor fills in the
 * 'type' discriminator" boilerplate. The tests exist to catch a typo'd
 * literal type string, which would otherwise only surface as a client
 * silently failing to route a message.
 */
class NetworkDTOsTest {

    @Test
    void joinAcceptedResponse_convenienceConstructor_setsType() {
        JoinAcceptedResponse r = new JoinAcceptedResponse("alice", 'W', 1200);

        assertEquals("JOIN_ACCEPTED", r.type());
        assertEquals("alice", r.username());
        assertEquals('W', r.color());
        assertEquals(1200, r.rating());
    }

    @Test
    void joinRejectedResponse_convenienceConstructor_setsType() {
        JoinRejectedResponse r = new JoinRejectedResponse("Room does not exist");

        assertEquals("JOIN_REJECTED", r.type());
        assertEquals("Room does not exist", r.reason());
    }

    @Test
    void gameStartedResponse_convenienceConstructor_wrapsUsersInList() {
        GameStartedResponse r = new GameStartedResponse("alice", "bob");

        assertEquals("GAME_STARTED", r.type());
        assertEquals(List.of("alice", "bob"), r.data());
    }

    @Test
    void reconnectAcceptedResponse_convenienceConstructor_setsType() {
        ReconnectAcceptedResponse r = new ReconnectAcceptedResponse("alice", 'B', 1500);

        assertEquals("RECONNECT_ACCEPTED", r.type());
        assertEquals('B', r.color());
    }

    @Test
    void reconnectRejectedResponse_convenienceConstructor_setsType() {
        ReconnectRejectedResponse r = new ReconnectRejectedResponse("No active game to reconnect to");

        assertEquals("RECONNECT_REJECTED", r.type());
    }

    @Test
    void loginSuccessResponse_convenienceConstructor_setsType() {
        LoginSuccessResponse r = new LoginSuccessResponse("alice", 1200);

        assertEquals("LOGIN_SUCCESS", r.type());
        assertEquals(1200, r.rating());
    }

    @Test
    void loginRejectedResponse_convenienceConstructor_setsType() {
        LoginRejectedResponse r = new LoginRejectedResponse("Invalid password or database error");

        assertEquals("LOGIN_REJECTED", r.type());
    }

    @Test
    void boardUpdateResponse_convenienceConstructor_setsType() {
        GameSnapshot snapshot = new GameSnapshot(List.of(), false);
        BoardUpdateResponse r = new BoardUpdateResponse(snapshot);

        assertEquals("BOARD_UPDATE", r.type());
        assertSame(snapshot, r.snapshot());
    }

    @Test
    void gameOverResponse_convenienceConstructor_setsType() {
        GameOverResponse r = new GameOverResponse("alice", "CHECKMATE");

        assertEquals("GAME_OVER", r.type());
        assertEquals("alice", r.winner());
        assertEquals("CHECKMATE", r.reason());
    }

    @Test
    void disconnectCountdownResponse_convenienceConstructor_setsType() {
        DisconnectCountdownResponse r = new DisconnectCountdownResponse(20, "alice");

        assertEquals("DISCONNECT_COUNTDOWN", r.type());
        assertEquals(20, r.seconds());
        assertEquals("alice", r.winnerIfTimeout());
    }

    @Test
    void disconnectCancelledResponse_convenienceConstructor_setsType() {
        DisconnectCancelledResponse r = new DisconnectCancelledResponse();

        assertEquals("DISCONNECT_CANCELLED", r.type());
    }

    @Test
    void joinRequest_fullConstructor_isPlainDataCarrier() {
        JoinRequest r = new JoinRequest("JOIN_ROOM", "alice", "pw", "room1");

        assertEquals("JOIN_ROOM", r.type());
        assertEquals("alice", r.username());
        assertEquals("pw", r.password());
        assertEquals("room1", r.roomId());
    }

    @Test
    void loginRequest_fullConstructor_isPlainDataCarrier() {
        LoginRequest r = new LoginRequest("LOGIN", "alice", "pw");

        assertEquals("alice", r.username());
        assertEquals("pw", r.password());
    }

    @Test
    void simpleEventResponse_carriesArbitraryData() {
        SimpleEventResponse r = new SimpleEventResponse("MOVE_LOGGED", List.of("12:00", "e2e4", 'W'));

        assertEquals("MOVE_LOGGED", r.type());
        assertEquals(3, r.data().size());
    }
}
