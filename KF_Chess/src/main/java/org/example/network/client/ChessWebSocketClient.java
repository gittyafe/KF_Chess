package org.example.network.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import lombok.extern.slf4j.Slf4j;
import org.example.bus.GameEventBus;
import org.example.bus.GameServerEvents;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Thin transport layer: owns the live {@link WebSocket}
 */
@Slf4j
public class ChessWebSocketClient implements WebSocket.Listener {

    private volatile WebSocket webSocket;
    private volatile String serverUrl;

    private volatile String pendingUsername;
    private volatile String pendingPassword;
    private volatile String currentUsername;
    private volatile String currentPassword;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OutgoingMessageFactory messages = new OutgoingMessageFactory(objectMapper);
    private final StringBuilder messageBuffer = new StringBuilder();

    private final ServerMessageDispatcher dispatcher = new ServerMessageDispatcher(new ServerMessageCallbacks() {
        @Override
        public void onLoginSuccess() {
            reconnectManager.markLoggedIn();
        }

        @Override
        public void requestJoinMatch(String roomId) {
            sendJoinMatch(roomId);
        }
    });

    private final ReconnectManager reconnectManager = new ReconnectManager(
            this::isConnected,
            this::attemptReconnect,
            () -> GameEventBus.getInstance().publish(GameServerEvents.RECONNECT_REJECTED, "Could not reach server")
    );

    public ChessWebSocketClient() {}

    public void connect(String serverUrl) {
        this.serverUrl = serverUrl;
        HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create(serverUrl), this)
                .thenAccept(ws -> this.webSocket = ws)
                .exceptionally(ex -> {
                    log.error("[CLIENT ERROR] Failed to connect: {}", ex.getMessage());
                    return null;
                });
    }

    private CompletableFuture<Void> attemptReconnect() {
        return HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create(serverUrl), this)
                .thenAccept(ws -> this.webSocket = ws); // onOpen() below fires the RECONNECT message
    }

    private boolean isConnected() {
        return webSocket != null && !webSocket.isOutputClosed();
    }

    public void sendMoveCommand(String command) {
        if (isConnected()) {
            webSocket.sendText(command, true);
        }
    }

    public void sendLogin(String username, String password) {
        if (isConnected()) {
            doSendLogin(username, password);
        } else {
            this.pendingUsername = username;
            this.pendingPassword = password;
        }
    }

    private void doSendLogin(String username, String password) {
        this.currentUsername = username;
        this.currentPassword = password;
        sendJson(() -> messages.login(username, password), "LOGIN");
    }

    private void sendReconnect() {
        if (!isConnected() || currentUsername == null || currentPassword == null) {
            return;
        }
        sendJson(() -> messages.reconnect(currentUsername, currentPassword), "RECONNECT");
        System.out.println("Sent RECONNECT for user: " + currentUsername);
    }

    public void sendJoinRoom(String roomId) {
        sendJoinPayload("JOIN_ROOM", roomId);
    }

    public void sendJoinMatch(String roomId) {
        sendJoinPayload("JOIN_MATCH", roomId);
    }

    private void sendJoinPayload(String type, String roomId) {
        if (!isConnected()) {
            return;
        }
        sendJson(() -> messages.join(type, roomId, currentUsername, currentPassword), type);
        System.out.println("Sent " + type + " for room: " + roomId);
    }

    public void sendCreateRoom(String roomId) {
        if (!isConnected()) {
            return;
        }
        sendJson(() -> messages.createRoom(roomId, currentUsername, currentPassword), "CREATE_ROOM");
        System.out.println("Sent CREATE_ROOM for room: " + roomId);
    }

    public void sendFindMatch() {
        if (isConnected()) {
            sendJson(messages::findMatch, "FIND_MATCH");
        }
    }

    public void sendCancelMatchmaking() {
        if (isConnected()) {
            sendJson(messages::cancelMatchmaking, "CANCEL_MATCHMAKING");
        }
    }

    @FunctionalInterface
    private interface JsonSupplier {
        String get() throws JsonProcessingException;
    }

    /** Every outgoing send used to repeat the same try/catch-and-log
     *  boilerplate around a Map.of(...) + writeValueAsString(...) call.
     *  This is that boilerplate, written once. */
    private void sendJson(JsonSupplier payloadSupplier, String context) {
        try {
            webSocket.sendText(payloadSupplier.get(), true);
        } catch (Exception e) {
            log.error("[CLIENT ERROR] Failed to send JSON message: {}", e.getMessage());
        }
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        log.info("[CLIENT OUT] Connected to chess server.");
        this.webSocket = webSocket;
        reconnectManager.reset();

        if (pendingUsername != null && pendingPassword != null) {
            String username = pendingUsername;
            String password = pendingPassword;
            pendingUsername = null;
            pendingPassword = null;
            doSendLogin(username, password);
        } else if (reconnectManager.hasLoggedInBefore() && currentUsername != null && currentPassword != null) {
            // We've logged in before and the socket just re-opened without a
            // fresh login click -- this is a reconnect after a drop.
            sendReconnect();
        }

        WebSocket.Listener.super.onOpen(webSocket);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        messageBuffer.append(data);

        if (last) {
            String message = messageBuffer.toString();
            messageBuffer.setLength(0);
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> root = objectMapper.readValue(message, Map.class);
                dispatcher.dispatch(root);
            } catch (Exception e) {
                log.error("[CLIENT ERROR] Error processing network message: {}", e.getMessage());
            }
        }

        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        log.info("[CLIENT IN] WebSocket closed with status {}: {}", statusCode, reason);
        reconnectManager.onDisconnected(currentUsername != null);
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        log.error("[CLIENT ERROR] WebSocket error: {}", error.getMessage());
        reconnectManager.onDisconnected(currentUsername != null);
        WebSocket.Listener.super.onError(webSocket, error);
    }
}
