package org.example.network.server.connection;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.database.UserRepository;
import org.example.network.server.room.GameRoom;
import org.example.network.server.room.MatchmakingManager;
import org.example.network.server.room.PlayerInfo;
import org.example.network.server.room.RoomRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MessageHandlerTest {

    private ObjectMapper objectMapper;
    private AuthHandler authHandler;
    private MatchmakingManager matchmakingManager;
    private UserRepository userRepository;
    private MessageHandler messageHandler;
    private RoomRegistry registry;
    private WebSocketSession session;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        authHandler = mock(AuthHandler.class);
        matchmakingManager = mock(MatchmakingManager.class);
        userRepository = mock(UserRepository.class);
        messageHandler = new MessageHandler(objectMapper, authHandler, matchmakingManager, userRepository);
        registry = new RoomRegistry();
        session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
    }

    @Test
    void login_routesToAuthHandler() {
        String payload = "{\"type\":\"LOGIN\",\"username\":\"a\",\"password\":\"p\"}";

        messageHandler.processMessage(session, payload, registry);

        verify(authHandler).processLoginRequest(session, payload, registry);
    }

    @Test
    void reconnect_routesToAuthHandler() {
        String payload = "{\"type\":\"RECONNECT\",\"username\":\"a\",\"password\":\"p\"}";

        messageHandler.processMessage(session, payload, registry);

        verify(authHandler).processReconnectRequest(session, payload, registry);
    }

    @Test
    void joinRoom_routesToAuthHandler() {
        String payload = "{\"type\":\"JOIN_ROOM\",\"username\":\"a\",\"password\":\"p\",\"roomId\":\"r1\"}";

        messageHandler.processMessage(session, payload, registry);

        verify(authHandler).processJoinRoomRequest(session, payload, registry);
    }

    @Test
    void joinMatch_routesToAuthHandler() {
        String payload = "{\"type\":\"JOIN_MATCH\",\"username\":\"a\",\"password\":\"p\",\"roomId\":\"r1\"}";

        messageHandler.processMessage(session, payload, registry);

        verify(authHandler).processJoinMatchRequest(session, payload, registry);
    }

    @Test
    void cancelMatchmaking_removesFromQueue_andSendsConfirmation() throws IOException {
        String payload = "{\"type\":\"CANCEL_MATCHMAKING\"}";

        messageHandler.processMessage(session, payload, registry);

        verify(matchmakingManager).removeFromQueue(session);
        verify(session).sendMessage(argThat((TextMessage m) -> m.getPayload().contains("MATCHMAKING_CANCELLED")));
    }

    @Test
    void unknownType_isIgnoredWithoutThrowing() {
        String payload = "{\"type\":\"NOT_A_REAL_TYPE\"}";

        assertDoesNotThrow(() -> messageHandler.processMessage(session, payload, registry));
        verifyNoInteractions(authHandler, matchmakingManager);
    }

    @Test
    void missingType_isIgnored() {
        String payload = "{\"foo\":\"bar\"}";

        messageHandler.processMessage(session, payload, registry);

        verifyNoInteractions(authHandler, matchmakingManager);
    }

    @Test
    void malformedJson_isCaughtAndLogged_withoutThrowing() {
        assertDoesNotThrow(() -> messageHandler.processMessage(session, "{not json", registry));
    }

    @Test
    void createRoom_emptyRoomId_sendsRejection() throws IOException {
        String payload = "{\"type\":\"CREATE_ROOM\",\"roomId\":\"\",\"username\":\"alice\"}";

        messageHandler.processMessage(session, payload, registry);

        verify(session).sendMessage(argThat((TextMessage m) -> m.getPayload().contains("CREATE_REJECTED")));
    }

    @Test
    void createRoom_duplicateId_sendsRejection() throws IOException {
        registry.tryCreateRoom("room1");
        String payload = "{\"type\":\"CREATE_ROOM\",\"roomId\":\"room1\",\"username\":\"alice\"}";

        messageHandler.processMessage(session, payload, registry);

        verify(session).sendMessage(argThat((TextMessage m) -> m.getPayload().contains("CREATE_REJECTED")));
    }

    @Test
    void createRoom_success_bindsCreatorAsWhite_andSendsAccepted() throws IOException {
        String payload = "{\"type\":\"CREATE_ROOM\",\"roomId\":\"room1\",\"username\":\"alice\"}";

        messageHandler.processMessage(session, payload, registry);

        assertNotNull(registry.getRoom("room1"));
        assertEquals("alice", registry.getPlayer(session).username());
        assertEquals('W', registry.getPlayer(session).color());
        verify(session).sendMessage(argThat((TextMessage m) -> m.getPayload().contains("CREATE_ACCEPTED")));
    }

    @Test
    void findMatch_loggedInPlayer_addsToQueue() {
        registry.registerPlayerInfo(session, "alice", 'W');
        when(userRepository.getRating("alice")).thenReturn(1400);
        String payload = "{\"type\":\"FIND_MATCH\"}";

        messageHandler.processMessage(session, payload, registry);

        verify(matchmakingManager).addToQueue(session, "alice", 1400);
    }

    @Test
    void findMatch_notLoggedIn_sendsRejection() throws IOException {
        String payload = "{\"type\":\"FIND_MATCH\"}";

        messageHandler.processMessage(session, payload, registry);

        verify(matchmakingManager, never()).addToQueue(any(), anyString(), anyInt());
        verify(session).sendMessage(argThat((TextMessage m) -> m.getPayload().contains("MATCHMAKING_REJECTED")));
    }

    // ---------- raw (non-JSON) game commands ----------

    @Test
    void gameCommand_noPlayerBound_isIgnored() {
        assertDoesNotThrow(() -> messageHandler.processMessage(session, "WPe2e4", registry));
    }

    @Test
    void gameCommand_roomNotStarted_isIgnored() {
        GameRoom room = mock(GameRoom.class);
        when(room.isStarted()).thenReturn(false);
        registry.bindParticipant(session, "alice", room, 'W');

        assertDoesNotThrow(() -> messageHandler.processMessage(session, "WPe2e4", registry));
    }

    @Test
    void gameCommand_playerAndStartedRoomPresent_doesNotThrow() {
        GameRoom room = mock(GameRoom.class);
        when(room.isStarted()).thenReturn(true);
        registry.bindParticipant(session, "alice", room, 'W');

        // GameCommandHandler itself is exercised in GameCommandHandlerTest;
        // here we just confirm MessageHandler successfully dispatches to it
        // without throwing when the room/engine mocks return nulls.
        assertDoesNotThrow(() -> messageHandler.processMessage(session, "WPe2e4", registry));
    }
}
