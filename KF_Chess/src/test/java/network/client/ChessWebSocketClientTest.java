package network.client;

import org.example.network.client.ChessWebSocketClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ChessWebSocketClient's collaborators (ReconnectManager, dispatcher,
 * message factory) are real final fields it builds itself, so these tests
 * exercise it mostly end-to-end against a mocked java.net.http.WebSocket
 * (an interface, easily mockable) rather than replacing internals. Private
 * non-final fields (webSocket, pending/current username & password) are
 * set/read via reflection to control and observe connection state without
 * a real network call.
 */
class ChessWebSocketClientTest {

    private ChessWebSocketClient client;
    private WebSocket mockSocket;

    @BeforeEach
    void setUp() {
        client = new ChessWebSocketClient();
        mockSocket = mock(WebSocket.class);
        when(mockSocket.sendText(anyString(), anyBoolean())).thenReturn(CompletableFuture.completedFuture(mockSocket));
    }

    private void setField(String name, Object value) throws Exception {
        Field f = ChessWebSocketClient.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(client, value);
    }

    private Object getField(String name) throws Exception {
        Field f = ChessWebSocketClient.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(client);
    }

    private void connectWithMockSocket() throws Exception {
        when(mockSocket.isOutputClosed()).thenReturn(false);
        setField("webSocket", mockSocket);
    }

    // ---------- not connected: everything is a safe no-op ----------

    @Test
    void sendMoveCommand_notConnected_doesNotThrow_andSendsNothing() {
        assertDoesNotThrow(() -> client.sendMoveCommand("WPe2e4"));
    }

    @Test
    void sendJoinRoom_notConnected_isNoOp() {
        assertDoesNotThrow(() -> client.sendJoinRoom("room1"));
    }

    @Test
    void sendCreateRoom_notConnected_isNoOp() {
        assertDoesNotThrow(() -> client.sendCreateRoom("room1"));
    }

    @Test
    void sendFindMatch_notConnected_isNoOp() {
        assertDoesNotThrow(() -> client.sendFindMatch());
    }

    @Test
    void sendCancelMatchmaking_notConnected_isNoOp() {
        assertDoesNotThrow(() -> client.sendCancelMatchmaking());
    }

    @Test
    void sendLogin_notConnected_queuesPendingCredentials() throws Exception {
        client.sendLogin("alice", "pw");

        assertEquals("alice", getField("pendingUsername"));
        assertEquals("pw", getField("pendingPassword"));
    }

    // ---------- connected: sends the expected wire format ----------

    @Test
    void sendMoveCommand_connected_sendsRawCommandText() throws Exception {
        connectWithMockSocket();

        client.sendMoveCommand("WPe2e4");

        verify(mockSocket).sendText("WPe2e4", true);
    }

    @Test
    void sendLogin_connected_sendsLoginJson_immediately() throws Exception {
        connectWithMockSocket();

        client.sendLogin("alice", "pw");

        verify(mockSocket).sendText(argThat((CharSequence json) -> json.toString().contains("\"LOGIN\"") && json.toString().contains("alice")), eq(true));
        assertEquals("alice", getField("currentUsername"));
        assertEquals("pw", getField("currentPassword"));
    }

    @Test
    void sendJoinRoom_connected_sendsJoinRoomJson() throws Exception {
        connectWithMockSocket();
        setField("currentUsername", "alice");
        setField("currentPassword", "pw");

        client.sendJoinRoom("room1");

        verify(mockSocket).sendText(argThat((CharSequence json) -> json.toString().contains("JOIN_ROOM") && json.toString().contains("room1")), eq(true));
    }

    @Test
    void sendJoinMatch_connected_sendsJoinMatchJson() throws Exception {
        connectWithMockSocket();
        setField("currentUsername", "alice");
        setField("currentPassword", "pw");

        client.sendJoinMatch("match_1");

        verify(mockSocket).sendText(argThat((CharSequence json) -> json.toString().contains("JOIN_MATCH") && json.toString().contains("match_1")), eq(true));
    }

    @Test
    void sendCreateRoom_connected_sendsCreateRoomJson() throws Exception {
        connectWithMockSocket();
        setField("currentUsername", "alice");
        setField("currentPassword", "pw");

        client.sendCreateRoom("room1");

        verify(mockSocket).sendText(argThat((CharSequence json) -> json.toString().contains("CREATE_ROOM") && json.toString().contains("room1")), eq(true));
    }

    @Test
    void sendFindMatch_connected_sendsFindMatchJson() throws Exception {
        connectWithMockSocket();

        client.sendFindMatch();

        verify(mockSocket).sendText(argThat((CharSequence json) -> json.toString().contains("FIND_MATCH")), eq(true));
    }

    @Test
    void sendCancelMatchmaking_connected_sendsCancelJson() throws Exception {
        connectWithMockSocket();

        client.sendCancelMatchmaking();

        verify(mockSocket).sendText(argThat((CharSequence json) -> json.toString().contains("CANCEL_MATCHMAKING")), eq(true));
    }

    // ---------- onOpen ----------

    @Test
    void onOpen_withPendingLogin_sendsLoginAndClearsPendingFields() throws Exception {
        when(mockSocket.isOutputClosed()).thenReturn(false);
        setField("pendingUsername", "alice");
        setField("pendingPassword", "pw");

        client.onOpen(mockSocket);

        verify(mockSocket).sendText(argThat((CharSequence json) -> json.toString().contains("\"LOGIN\"")), eq(true));
        assertNull(getField("pendingUsername"));
        assertNull(getField("pendingPassword"));
    }

    @Test
    void onOpen_noPendingLogin_andNeverLoggedIn_sendsNothing() throws Exception {
        when(mockSocket.isOutputClosed()).thenReturn(false);

        client.onOpen(mockSocket);

        verify(mockSocket, never()).sendText(anyString(), anyBoolean());
    }

    @Test
    void onOpen_reopenedAfterPriorLogin_sendsReconnect() throws Exception {
        when(mockSocket.isOutputClosed()).thenReturn(false);
        setField("currentUsername", "alice");
        setField("currentPassword", "pw");

        Object reconnectManager = getField("reconnectManager");
        java.lang.reflect.Method markLoggedIn = reconnectManager.getClass().getDeclaredMethod("markLoggedIn");
        markLoggedIn.setAccessible(true);
        markLoggedIn.invoke(reconnectManager);

        client.onOpen(mockSocket);

        verify(mockSocket).sendText(argThat((CharSequence json) -> json.toString().contains("\"RECONNECT\"") && json.toString().contains("alice")), eq(true));
    }

    // ---------- onText ----------

    @Test
    void onText_malformedJson_doesNotThrow_andRequestsMoreData() {
        when(mockSocket.isOutputClosed()).thenReturn(false);

        assertDoesNotThrow(() -> client.onText(mockSocket, "not valid json", true));

        verify(mockSocket).request(1);
    }

    @Test
    void onText_partialFragment_doesNotDispatchUntilLastIsTrue() {
        // "last=false" should just buffer -- no exception, no dispatch attempted yet.
        assertDoesNotThrow(() -> client.onText(mockSocket, "{\"type\":\"LOGIN_", false));
        verify(mockSocket).request(1);
    }

    @Test
    void onText_completeValidJson_processesWithoutThrowing() {
        assertDoesNotThrow(() ->
                client.onText(mockSocket, "{\"type\":\"MATCHMAKING_CANCELLED\"}", true));
        verify(mockSocket).request(1);
    }

    // ---------- onClose / onError ----------

    @Test
    void onClose_doesNotThrow() {
        assertDoesNotThrow(() -> client.onClose(mockSocket, 1000, "normal closure"));
    }

    @Test
    void onError_doesNotThrow() {
        assertDoesNotThrow(() -> client.onError(mockSocket, new RuntimeException("boom")));
    }
}
