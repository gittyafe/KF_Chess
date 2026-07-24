package org.example.network.client;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Builds the JSON strings the chess server expects for each outgoing
 * command. Pure functions -- no socket, no I/O, no static state -- so
 * they're easy to unit test on their own ("does login() produce the right
 * JSON for these inputs?") without spinning up a WebSocket at all.
 */
final class OutgoingMessageFactory {

    private final ObjectMapper objectMapper;

    OutgoingMessageFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String login(String username, String password) throws JsonProcessingException {
        return objectMapper.writeValueAsString(Map.of(
                "type", "LOGIN",
                "username", username,
                "password", password));
    }

    String reconnect(String username, String password) throws JsonProcessingException {
        return objectMapper.writeValueAsString(Map.of(
                "type", "RECONNECT",
                "username", username,
                "password", password));
    }

    /** Shared shape for both JOIN_ROOM and JOIN_MATCH -- pass the type. */
    String join(String type, String roomId, String username, String password) throws JsonProcessingException {
        return objectMapper.writeValueAsString(Map.of(
                "type", type,
                "roomId", roomId,
                "username", username != null ? username : "",
                "password", password != null ? password : ""));
    }

    String createRoom(String roomId, String username, String password) throws JsonProcessingException {
        return objectMapper.writeValueAsString(Map.of(
                "type", "CREATE_ROOM",
                "roomId", roomId,
                "username", username != null ? username : "",
                "password", password != null ? password : ""));
    }

    String findMatch() throws JsonProcessingException {
        return objectMapper.writeValueAsString(Map.of("type", "FIND_MATCH"));
    }

    String cancelMatchmaking() throws JsonProcessingException {
        return objectMapper.writeValueAsString(Map.of("type", "CANCEL_MATCHMAKING"));
    }
}
