package org.example.network.server.room;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.engines.GameEngine;
import org.example.network.nats.NatsBridge;
import org.example.network.protocol.NetworkDTOs.*;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Owns all outbound communication for a room: JSON serialization and
 * sending/broadcasting to sessions/NATS. GameRoom and its other
 * collaborators describe *what* happened; this is the only place that
 * knows *how* that becomes a WebSocket message.
 */
@Slf4j
public class RoomMessenger {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String roomId;
    private final RoomPlayers players;
    private final GameEngine gameEngine;

    public RoomMessenger(String roomId, RoomPlayers players, GameEngine gameEngine) {
        this.roomId = roomId;
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
                log.error("[SERVER ERROR] Error sending state to spectator: {}", e.getMessage());
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
        try {
            String json = objectMapper.writeValueAsString(new GameOverResponse(winner == null ? "" : winner, reason));
            log.info("[SERVER OUT] Publishing GAME_OVER to room [{}]: {}", roomId, json);
            broadcastRaw(json);
        } catch (Exception e) {
            log.error("[SERVER ERROR] Error broadcasting GAME_OVER: {}", e.getMessage());
        }
    }

    public void broadcastDisconnectCountdown(int seconds, String winnerIfTimeout) {
        sendToAll(new DisconnectCountdownResponse(seconds, winnerIfTimeout));
    }

    public void broadcastDisconnectCancelled() {
        sendToAll(new DisconnectCancelledResponse());
    }

    /** Escape hatch for callers that still build their own JSON (kept so GameRoom's public API doesn't change). */
    public void broadcastRaw(String json) {
        if (this.roomId != null) {
            NatsBridge.publish("game.updates." + this.roomId, json);
        } else {
            log.warn("[ROOM MESSENGER WARN] roomId is null, cannot publish to NATS");
        }
    }

    private void sendToAll(Object dto) {
        try {
            broadcastRaw(objectMapper.writeValueAsString(dto));
        } catch (Exception e) {
            log.error("[SERVER ERROR] Error broadcasting message: {}", e.getMessage());
        }
    }

    /**
     * Sends to exactly one session -- never to the whole room.
     *
     * <p><b>This used to be a real bug:</b> the old version fell back to
     * {@code broadcastRaw(json)} -- i.e. the whole room's NATS channel --
     * whenever the session wasn't open *locally*. In the distributed
     * setup that's the *normal* case (a reconnecting/joining player's
     * socket lives on a WS Gateway process, this code runs on a Game
     * Shard process), so every reconnect or spectator join was blasting
     * that person's private "game started" + full board snapshot to
     * their opponent and every spectator in the room too, repeatedly.
     *
     * <p>The fix: if we don't have a live local socket for this session
     * (including the normal in-process case, and the
     * {@code NatsWebSocketSessionAdapter} case -- its {@code isOpen()} is
     * always {@code true} and its {@code sendMessage()} already publishes
     * correctly), publish to that session's own inbox,
     * {@code "gateway.outbound.<sessionId>"} -- the exact channel
     * {@code ChessWebSocketHandler} already listens on to deliver to one
     * specific client, regardless of which shard produced the message.
     */
    private void sendTo(WebSocketSession session, Object dto) throws Exception {
        String json = objectMapper.writeValueAsString(dto);

        if (session == null) {
            log.warn("[ROOM MESSENGER WARN] sendTo() called with a null session; message dropped.");
            return;
        }

        if (session.isOpen()) {
            session.sendMessage(new TextMessage(json));
        } else {
            NatsBridge.publish("gateway.outbound." + session.getId(), json);
        }
    }

    private volatile String lastBroadcastedStateJson;

    public void broadcastGameStateIfChanged() {
        try {
            String json = objectMapper.writeValueAsString(new BoardUpdateResponse(gameEngine.getSnapshot()));
            if (json.equals(lastBroadcastedStateJson)) {
                return; // nothing changed since the last tick -- skip the network round-trip entirely
            }
            lastBroadcastedStateJson = json;
            broadcastRaw(json);
        } catch (Exception e) {
            log.error("[SERVER ERROR] Error broadcasting game state: {}", e.getMessage());
        }
    }

    /**
     * Lightweight heartbeat for client-side idle animations (piece
     * breathing, cooldown countdown visuals, etc.) that need a periodic
     * clock signal even when nothing in the actual game state has changed.
     * Deliberately NOT a full board snapshot -- just a server timestamp --
     * so it stays cheap even sent frequently. Complements, not replaces,
     * broadcastGameStateIfChanged(): that one is event-driven per
     * Server_Design.md and skips idle ticks entirely; this one exists
     * specifically to cover the gap that leaves for animation timing.
     */
    public void broadcastHeartbeat() {
        sendToAll(new SimpleEventResponse("HEARTBEAT", List.of(System.currentTimeMillis())));
    }
}
