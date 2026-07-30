package network.server;

import org.example.network.server.connection.ChessWebSocketHandler;
import org.example.network.server.connection.MessageHandler;
import org.example.network.server.room.GameRoom;
import org.example.network.server.room.MatchmakingManager;
import org.example.network.server.room.RoomRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ChessWebSocketHandler builds its RoomRegistry/MatchmakingManager/
 * MessageHandler collaborators itself in the constructor rather than
 * accepting them via dependency injection, so we swap in mocks via
 * reflection after construction to isolate the lifecycle logic
 * (afterConnectionClosed) from real message parsing/game logic.
 */
class ChessWebSocketHandlerTest {

    private ChessWebSocketHandler handler;
    private RoomRegistry registry;
    private MatchmakingManager matchmakingManager;
    private MessageHandler messageHandler;
    private WebSocketSession session;

    @BeforeEach
    void setUp() throws Exception {
        handler = new ChessWebSocketHandler();

        registry = mock(RoomRegistry.class);
        matchmakingManager = mock(MatchmakingManager.class);
        messageHandler = mock(MessageHandler.class);

        setPrivateField(handler, "registry", registry);
        setPrivateField(handler, "matchmakingManager", matchmakingManager);
        setPrivateField(handler, "messageHandler", messageHandler);

        session = mock(WebSocketSession.class);
    }

    private static void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    void handleTextMessage_blankPayload_isIgnored() throws Exception {
        handler.handleTextMessage(session, new TextMessage("   "));

        verifyNoInteractions(messageHandler);
    }

    @Test
    void handleTextMessage_nonBlankPayload_dispatchesToMessageHandler() throws Exception {
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"LOGIN\"}"));

        verify(messageHandler).processMessage(eq(session), eq("{\"type\":\"LOGIN\"}"), eq(registry));
    }

    @Test
    void afterConnectionClosed_noRoomForSession_onlyClearsMatchmakingQueue() throws Exception {
        when(registry.dropSession(session)).thenReturn(null);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(matchmakingManager).removeFromQueue(session);
    }

    @Test
    void afterConnectionClosed_spectatorLeaves_roomNotTouchedFurther() throws Exception {
        GameRoom room = mock(GameRoom.class);
        when(registry.dropSession(session)).thenReturn(room);
        when(room.isSpectator(session)).thenReturn(true);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(room).removeSession(session);
        verify(room, never()).handlePlayerDisconnect(any());
        verify(room, never()).shutdown();
    }

    @Test
    void afterConnectionClosed_playerDisconnectsMidGame_startsResignCountdown() {
        GameRoom room = mock(GameRoom.class);
        when(registry.dropSession(session)).thenReturn(room);
        when(room.isSpectator(session)).thenReturn(false);
        when(room.isStarted()).thenReturn(true);
        when(room.isEnded()).thenReturn(false);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(room).handlePlayerDisconnect(session);
        verify(room, never()).shutdown();
    }

    @Test
    void afterConnectionClosed_abandonedRoom_isTornDown() {
        GameRoom room = mock(GameRoom.class);
        when(registry.dropSession(session)).thenReturn(room);
        when(room.isSpectator(session)).thenReturn(false);
        when(room.isStarted()).thenReturn(false);
        when(room.getSessions()).thenReturn(java.util.Collections.emptyList());

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(registry).unregisterRoomIfAbandoned(room);
        verify(room).shutdown();
    }

    @Test
    void afterConnectionClosed_notStartedButOthersStillPresent_doesNothingFurther() {
        GameRoom room = mock(GameRoom.class);
        when(registry.dropSession(session)).thenReturn(room);
        when(room.isSpectator(session)).thenReturn(false);
        when(room.isStarted()).thenReturn(false);
        when(room.getSessions()).thenReturn(java.util.List.of(mock(WebSocketSession.class)));

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(registry, never()).unregisterRoomIfAbandoned(any());
        verify(room, never()).shutdown();
    }

    @Test
    void afterConnectionClosed_gameAlreadyEnded_doesNotStartCountdownOrShutdown() {
        GameRoom room = mock(GameRoom.class);
        when(registry.dropSession(session)).thenReturn(room);
        when(room.isSpectator(session)).thenReturn(false);
        when(room.isStarted()).thenReturn(true);
        when(room.isEnded()).thenReturn(true);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(room, never()).handlePlayerDisconnect(any());
        verify(room, never()).shutdown();
    }
}
