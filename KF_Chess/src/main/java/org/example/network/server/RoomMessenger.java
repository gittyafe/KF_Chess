package org.example.network.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.engines.GameEngine;
import org.example.network.protocol.NetworkDTOs.*;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Owns all outbound communication for a room: JSON serialization and
 * sending/broadcasting to sessions. GameRoom and its other collaborators
 * describe *what* happened; this is the only place that knows *how* that
 * becomes a WebSocket message.
 */
public class RoomMessenger {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RoomPlayers players;
    private final GameEngine gameEngine;

    public RoomMessenger(RoomPlayers players, GameEngine gameEngine) {
        this.players = players;
        this.gameEngine = gameEngine;
    }

    public void broadcastGameStarted() {
        sendToAll(new GameStartedResponse(players.getWhiteUsername(), players.getBlackUsername()));
    }

    /** Brings a single session (a reconnecting player, or a late-joining spectator) up to date. */
    public void sendGameStateTo(WebSocketSession session) {
        CompletableFuture.runAsync(() -> {
            try {
                sendTo(session, new GameStartedResponse(players.getWhiteUsername(), players.getBlackUsername()));
                Thread.sleep(100);
                sendTo(session, new BoardUpdateResponse(gameEngine.getSnapshot()));
            } catch (Exception e) {
                System.err.println("Error sending state to spectator: " + e.getMessage());
            }
        });
    }

    public void broadcastGameState() {
        sendToAll(new BoardUpdateResponse(gameEngine.getSnapshot()));
    }

    public void broadcastEvent(String type, List<Object> data) {
        sendToAll(new SimpleEventResponse(type, data));
    }

    public void broadcastGameOver(String winner, String reason) {
        sendToAll(new GameOverResponse(winner == null ? "" : winner, reason));
    }

    public void broadcastDisconnectCountdown(int seconds, String winnerIfTimeout) {
        sendToAll(new DisconnectCountdownResponse(seconds, winnerIfTimeout));
    }

    public void broadcastDisconnectCancelled() {
        sendToAll(new DisconnectCancelledResponse());
    }

    /** Escape hatch for callers that still build their own JSON (kept so GameRoom's public API doesn't change). */
    public void broadcastRaw(String json) {
        TextMessage msg = new TextMessage(json);
        for (WebSocketSession session : players.getSessions()) {
            try {
                if (session.isOpen()) session.sendMessage(msg);
            } catch (Exception ignored) {}
        }
    }

    private void sendToAll(Object dto) {
        try {
            broadcastRaw(objectMapper.writeValueAsString(dto));
        } catch (Exception e) {
            System.err.println("Error broadcasting message: " + e.getMessage());
        }
    }

    private void sendTo(WebSocketSession session, Object dto) throws Exception {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(dto)));
        }
    }
}
