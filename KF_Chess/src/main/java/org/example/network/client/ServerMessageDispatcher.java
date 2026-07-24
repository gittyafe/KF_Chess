package org.example.network.client;

import static org.example.network.client.JsonFields.asList;
import static org.example.network.client.JsonFields.asMap;
import static org.example.network.client.JsonFields.charValue;
import static org.example.network.client.JsonFields.intValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.example.bus.GameEventBus;
import org.example.bus.GameServerEvents;
import org.example.engines.GameSnapshot;

/**
 * Takes one already-decoded server message (a {@code Map<String,Object>}
 * with a "type" field) and republishes it on {@link GameEventBus} under
 * the matching {@link GameServerEvents} name.
 */
final class ServerMessageDispatcher {

    private final ServerMessageCallbacks callbacks;
    private final Map<String, Consumer<Map<String, Object>>> handlers = new HashMap<>();

    ServerMessageDispatcher(ServerMessageCallbacks callbacks) {
        this.callbacks = callbacks;
        registerHandlers();
    }

    void dispatch(Map<String, Object> root) {
        String type = (String) root.get("type");
        Consumer<Map<String, Object>> handler = handlers.get(type);
        if (handler != null) {
            handler.accept(root);
        }
    }

    private void publish(String event, Object payload) {
        GameEventBus.getInstance().publish(event, payload);
    }

    private void registerHandlers() {
        handlers.put("BOARD_UPDATE", root -> {
            GameSnapshot snapshot = GameSnapshotMapper.fromMap(asMap(root.get("snapshot")));
            if (!snapshot.pieces().isEmpty()) {
                publish(GameServerEvents.BOARD_UPDATE_RECEIVED, snapshot);
            }
        });

        handlers.put("DISCONNECT_COUNTDOWN", root -> {
            int seconds = intValue(root.get("seconds"));
            System.out.println("Received DISCONNECT_COUNTDOWN from server: " + seconds + "s");
            publish(GameServerEvents.DISCONNECT_COUNTDOWN, seconds);
        });

        handlers.put("DISCONNECT_CANCELLED", root -> {
            System.out.println("Received DISCONNECT_CANCELLED from server");
            publish(GameServerEvents.DISCONNECT_CANCELLED, null);
        });

        handlers.put("RECONNECT_ACCEPTED", root -> {
            String username = (String) root.get("username");
            char color = charValue(root.get("color"));
            int rating = intValue(root.get("rating"));
            System.out.println("Reconnected successfully as " + username);
            publish(GameServerEvents.RECONNECT_ACCEPTED, new Object[]{ username, color, rating });
        });

        handlers.put("RECONNECT_REJECTED", root -> {
            String reason = (String) root.get("reason");
            System.err.println("Reconnect rejected: " + reason);
            publish(GameServerEvents.RECONNECT_REJECTED, reason);
        });

        handlers.put("GAME_OVER", root -> {
            String winner = (String) root.get("winner");
            String reason = (String) root.get("reason");
            System.out.println("Game Over received. Winner: " + winner);
            publish(GameServerEvents.GAME_OVER, new Object[]{ winner, reason });
        });

        handlers.put("CREATE_ACCEPTED", root ->
                publish(GameServerEvents.CREATE_ACCEPTED, root.get("username")));

        handlers.put("CREATE_REJECTED", root ->
                publish(GameServerEvents.CREATE_REJECTED, root.get("reason")));

        handlers.put("MOVE_LOGGED", root -> {
            List<Object> data = asList(root.get("data"));
            String time = (String) data.get(0);
            String moveNotation = (String) data.get(1);
            char color = charValue(data.get(2));
            publish(GameServerEvents.MOVE_LOGGED, new Object[]{ time, moveNotation, color });
        });

        handlers.put("PIECE_CAPTURED", root -> {
            List<Object> data = asList(root.get("data"));
            char capturedType = charValue(data.get(0));
            char capturingColor = charValue(data.get(1));
            publish(GameServerEvents.PIECE_CAPTURED, new Object[]{ capturedType, capturingColor });
        });

        handlers.put("JOIN_ACCEPTED", root -> {
            String username = (String) root.get("username");
            char color = charValue(root.get("color"));
            int rating = intValue(root.get("rating"));
            publish(GameServerEvents.JOIN_ACCEPTED, new Object[]{ username, color, rating });
        });

        handlers.put("JOIN_REJECTED", root ->
                publish(GameServerEvents.JOIN_REJECTED, root.get("reason")));

        handlers.put("GAME_STARTED", root -> {
            List<Object> players = asList(root.get("data"));
            publish(GameServerEvents.GAME_STARTED, players.toArray());
        });

        handlers.put("MATCHMAKING_STARTED", root ->
                publish(GameServerEvents.MATCHMAKING_STARTED, root.get("message")));

        handlers.put("MATCHMAKING_TIMEOUT", root ->
                publish(GameServerEvents.MATCHMAKING_TIMEOUT, root.get("reason")));

        handlers.put("MATCHMAKING_CANCELLED", root ->
                publish(GameServerEvents.MATCHMAKING_CANCELLED, null));

        handlers.put("MATCH_FOUND", root -> {
            String roomId = (String) root.get("roomId");
            String opponent = (String) root.get("opponent");
            System.out.println("Match found! Room: " + roomId + " against " + opponent);

            callbacks.requestJoinMatch(roomId);
            publish(GameServerEvents.MATCH_FOUND, new Object[]{ roomId, opponent });
        });

        handlers.put("LOGIN_SUCCESS", root -> {
            String username = (String) root.get("username");
            Object colorObj = root.get("color");
            char color = (colorObj != null) ? charValue(colorObj) : 'W';
            Object ratingObj = root.get("rating");
            int rating = (ratingObj != null) ? intValue(ratingObj) : 1200;

            callbacks.onLoginSuccess();
            publish(GameServerEvents.LOGIN_SUCCESS, new Object[]{ username, color, rating });
        });
    }
}
