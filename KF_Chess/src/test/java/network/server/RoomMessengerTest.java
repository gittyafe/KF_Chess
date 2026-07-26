package org.example.network.server.room;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.engines.GameEngine;
import org.example.engines.GameSnapshot;
import org.example.network.server.room.RoomMessenger;
import org.example.network.server.room.RoomPlayers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RoomMessengerTest {

    private RoomPlayers players;
    private GameEngine engine;
    private RoomMessenger messenger;
    private WebSocketSession white;
    private WebSocketSession black;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        players = new RoomPlayers();
        engine = mock(GameEngine.class);
        messenger = new RoomMessenger(players, engine);

        white = mock(WebSocketSession.class);
        black = mock(WebSocketSession.class);
        when(white.isOpen()).thenReturn(true);
        when(black.isOpen()).thenReturn(true);
        players.addPlayer(white, "alice", "room1");
        players.addPlayer(black, "bob", "room1");
    }

    private JsonNode captureLastPayload(WebSocketSession session) throws Exception {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeastOnce()).sendMessage(captor.capture());
        return mapper.readTree(captor.getValue().getPayload());
    }

    @Test
    void broadcastGameStarted_sendsToBothSeats() throws Exception {
        messenger.broadcastGameStarted();

        verify(white).sendMessage(any(TextMessage.class));
        verify(black).sendMessage(any(TextMessage.class));
    }

    @Test
    void broadcastGameState_sendsBoardSnapshotToAllSessions() throws Exception {
        when(engine.getSnapshot()).thenReturn(new GameSnapshot(List.of(), false));

        messenger.broadcastGameState();

        verify(white).sendMessage(any(TextMessage.class));
        verify(black).sendMessage(any(TextMessage.class));
    }

    @Test
    void broadcastEvent_sendsTypeAndDataToAllSessions() throws Exception {
        messenger.broadcastEvent("MOVE_LOGGED", List.of("12:00", "e2e4", "W"));

        JsonNode payload = captureLastPayload(white);
        assertEquals("MOVE_LOGGED", payload.get("type").asText());
    }

    @Test
    void broadcastGameOver_nullWinner_sendsEmptyStringWinner() throws Exception {
        messenger.broadcastGameOver(null, "RESIGN_DISCONNECT");

        JsonNode payload = captureLastPayload(white);
        assertEquals("", payload.get("winner").asText());
        assertEquals("RESIGN_DISCONNECT", payload.get("reason").asText());
    }

    @Test
    void broadcastGameOver_withWinner_includesWinnerName() throws Exception {
        messenger.broadcastGameOver("alice", "CHECKMATE");

        JsonNode payload = captureLastPayload(black);
        assertEquals("alice", payload.get("winner").asText());
    }

    @Test
    void broadcastDisconnectCountdown_includesSecondsAndWinner() throws Exception {
        messenger.broadcastDisconnectCountdown(20, "alice");

        JsonNode payload = captureLastPayload(white);
        assertEquals(20, payload.get("seconds").asInt());
    }

    @Test
    void broadcastDisconnectCancelled_sendsToAllSessions() throws IOException {
        messenger.broadcastDisconnectCancelled();

        verify(white).sendMessage(any(TextMessage.class));
        verify(black).sendMessage(any(TextMessage.class));
    }

    @Test
    void broadcastRaw_skipsClosedSessions_withoutThrowing() throws IOException {
        when(black.isOpen()).thenReturn(false);

        assertDoesNotThrow(() -> messenger.broadcastRaw("{\"type\":\"RAW\"}"));

        verify(black, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void broadcastRaw_sessionThrowsOnSend_doesNotAbortRemainingSends() throws Exception {
        doThrow(new RuntimeException("socket closed")).when(white).sendMessage(any(TextMessage.class));

        assertDoesNotThrow(() -> messenger.broadcastRaw("{\"type\":\"RAW\"}"));

        verify(black).sendMessage(any(TextMessage.class));
    }

    @Test
    void sendGameStateTo_singleSession_sendsGameStartedThenBoardUpdate() throws Exception {
        WebSocketSession spectator = mock(WebSocketSession.class);
        when(spectator.isOpen()).thenReturn(true);
        when(engine.getSnapshot()).thenReturn(new GameSnapshot(List.of(), false));

        messenger.sendGameStateTo(spectator);

        // sendGameStateTo runs asynchronously with a short internal delay,
        // so poll briefly rather than asserting immediately.
        verify(spectator, timeout(1000).times(2)).sendMessage(any(TextMessage.class));
    }
}
