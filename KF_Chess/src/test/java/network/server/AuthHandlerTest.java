package network.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.database.UserRepository;
import org.example.network.server.connection.AuthHandler;
import org.example.network.server.room.GameRoom;
import org.example.network.server.room.RoomRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * These tests send hand-built JSON matching the field names used by
 * AuthHandler when reading JoinRequest/LoginRequest (type/username/
 * password/roomId), which is how Jackson's native record support maps
 * record components to JSON properties by default. If your actual
 * NetworkDTOs records use different component names/@JsonProperty
 * overrides, adjust the JSON literals below to match.
 */
class AuthHandlerTest {

    private ObjectMapper objectMapper;
    private UserRepository userRepository;
    private AuthHandler authHandler;
    private RoomRegistry registry;
    private WebSocketSession session;
    private final Map<String, Object> sessionAttributes = new HashMap<>();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        userRepository = mock(UserRepository.class);
        authHandler = new AuthHandler(objectMapper, userRepository);
        registry = new RoomRegistry();
        session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(sessionAttributes);
    }

    private JsonNode lastResponse() throws Exception {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeastOnce()).sendMessage(captor.capture());
        return objectMapper.readTree(captor.getValue().getPayload());
    }

    // ---------- JOIN_ROOM ----------

    @Test
    void joinRoom_missingFields_sendsRejection() throws Exception {
        String payload = "{\"type\":\"JOIN_ROOM\",\"username\":\"\",\"password\":\"p\",\"roomId\":\"r1\"}";

        authHandler.processJoinRoomRequest(session, payload, registry);

        verify(session).sendMessage(any(TextMessage.class));
        assertTrue(lastResponse().toString().toLowerCase().contains("missing"));
    }

    @Test
    void joinRoom_authFailure_sendsRejection() throws Exception {
        when(userRepository.authenticateOrRegister("alice", "wrong")).thenReturn(-1);
        String payload = "{\"type\":\"JOIN_ROOM\",\"username\":\"alice\",\"password\":\"wrong\",\"roomId\":\"r1\"}";

        authHandler.processJoinRoomRequest(session, payload, registry);

        verify(session).sendMessage(any(TextMessage.class));
        assertTrue(lastResponse().toString().toLowerCase().contains("invalid"));
    }

    @Test
    void joinRoom_roomDoesNotExist_sendsRejection() throws Exception {
        when(userRepository.authenticateOrRegister("alice", "pw")).thenReturn(1200);
        String payload = "{\"type\":\"JOIN_ROOM\",\"username\":\"alice\",\"password\":\"pw\",\"roomId\":\"missing-room\"}";

        authHandler.processJoinRoomRequest(session, payload, registry);

        assertTrue(lastResponse().toString().toLowerCase().contains("does not exist"));
    }

    @Test
    void joinRoom_success_bindsParticipantAndSendsAccepted() throws Exception {
        registry.tryCreateRoom("r1");
        when(userRepository.authenticateOrRegister("alice", "pw")).thenReturn(1200);
        String payload = "{\"type\":\"JOIN_ROOM\",\"username\":\"alice\",\"password\":\"pw\",\"roomId\":\"r1\"}";

        authHandler.processJoinRoomRequest(session, payload, registry);

        assertNotNull(registry.getPlayer(session));
        assertEquals("alice", registry.getPlayer(session).username());
        assertSame(registry.getRoom("r1"), registry.getRoomForSession(session));
    }

    @Test
    void joinRoom_malformedJson_sendsRejection_withoutThrowing() throws IOException {
        assertDoesNotThrow(() ->
                authHandler.processJoinRoomRequest(session, "not-json", registry));
        verify(session).sendMessage(any(TextMessage.class));
    }

    @Test
    void joinRoom_wrongMessageType_isIgnored() throws IOException {
        String payload = "{\"type\":\"SOMETHING_ELSE\",\"username\":\"alice\",\"password\":\"pw\",\"roomId\":\"r1\"}";

        authHandler.processJoinRoomRequest(session, payload, registry);

        verify(session, never()).sendMessage(any(TextMessage.class));
    }

    // ---------- JOIN_MATCH ----------

    @Test
    void joinMatch_success_createsRoomIfMissing() {
        when(userRepository.authenticateOrRegister("alice", "pw")).thenReturn(1200);
        String payload = "{\"type\":\"JOIN_MATCH\",\"username\":\"alice\",\"password\":\"pw\",\"roomId\":\"match_1\"}";

        authHandler.processJoinMatchRequest(session, payload, registry);

        assertNotNull(registry.getRoom("match_1"));
        assertSame(registry.getRoom("match_1"), registry.getRoomForSession(session));
    }

    // ---------- LOGIN ----------

    @Test
    void login_invalidCredentials_sendsRejection() throws Exception {
        when(userRepository.authenticateOrRegister("alice", "bad")).thenReturn(-1);
        String payload = "{\"type\":\"LOGIN\",\"username\":\"alice\",\"password\":\"bad\"}";

        authHandler.processLoginRequest(session, payload, registry);

        assertTrue(lastResponse().toString().toLowerCase().contains("invalid"));
    }

    @Test
    void login_success_setsSessionAttributes_andSendsSuccess() throws Exception {
        when(userRepository.authenticateOrRegister("alice", "pw")).thenReturn(1300);
        String payload = "{\"type\":\"LOGIN\",\"username\":\"alice\",\"password\":\"pw\"}";

        authHandler.processLoginRequest(session, payload, registry);

        assertEquals("alice", sessionAttributes.get("username"));
        assertEquals(1300, sessionAttributes.get("rating"));
    }

    @Test
    void login_noActiveGame_registersTemporaryWhiteColor() {
        when(userRepository.authenticateOrRegister("alice", "pw")).thenReturn(1300);
        String payload = "{\"type\":\"LOGIN\",\"username\":\"alice\",\"password\":\"pw\"}";

        authHandler.processLoginRequest(session, payload, registry);

        assertEquals('W', registry.getPlayer(session).color());
    }

    @Test
    void login_withActiveGame_doublesAsReconnect() {
        // Seat alice as white in a real room so there's something active to
        // reconnect into.
        GameRoom room = registry.tryCreateRoom("r1");
        WebSocketSession originalSession = mock(WebSocketSession.class);
        when(originalSession.isOpen()).thenReturn(true);
        room.addPlayer(originalSession, "alice");
        registry.bindParticipant(originalSession, "alice", room, 'W');

        when(userRepository.authenticateOrRegister("alice", "pw")).thenReturn(1300);
        String payload = "{\"type\":\"LOGIN\",\"username\":\"alice\",\"password\":\"pw\"}";

        authHandler.processLoginRequest(session, payload, registry);

        assertSame(room, registry.getRoomForSession(session));
        assertEquals('W', registry.getPlayer(session).color());
    }

    @Test
    void login_malformedJson_sendsRejection_withoutThrowing() throws IOException {
        assertDoesNotThrow(() -> authHandler.processLoginRequest(session, "{bad json", registry));
        verify(session).sendMessage(any(TextMessage.class));
    }

    // ---------- RECONNECT ----------

    @Test
    void reconnect_missingCredentials_sendsRejection() throws Exception {
        String payload = "{\"username\":\"\",\"password\":\"\"}";

        authHandler.processReconnectRequest(session, payload, registry);

        assertTrue(lastResponse().toString().toLowerCase().contains("missing"));
    }

    @Test
    void reconnect_noActiveGame_sendsRejection() throws Exception {
        when(userRepository.authenticateOrRegister("alice", "pw")).thenReturn(1200);
        String payload = "{\"username\":\"alice\",\"password\":\"pw\"}";

        authHandler.processReconnectRequest(session, payload, registry);

        assertTrue(lastResponse().toString().toLowerCase().contains("no active game"));
    }

    @Test
    void reconnect_activeGame_rebindsSessionAndAccepts() throws Exception {
        GameRoom room = registry.tryCreateRoom("r1");
        WebSocketSession originalSession = mock(WebSocketSession.class);
        when(originalSession.isOpen()).thenReturn(true);
        room.addPlayer(originalSession, "alice");
        registry.bindParticipant(originalSession, "alice", room, 'W');

        when(userRepository.authenticateOrRegister("alice", "pw")).thenReturn(1200);
        String payload = "{\"username\":\"alice\",\"password\":\"pw\"}";

        authHandler.processReconnectRequest(session, payload, registry);

        assertSame(room, registry.getRoomForSession(session));
        // The important behavioral assertion: the new session is now bound
        // as the active participant for "alice" in the room.
        assertEquals("alice", registry.getPlayer(session).username());
    }
}
