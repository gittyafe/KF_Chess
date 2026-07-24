package org.example.network.protocol;

import org.example.engines.GameSnapshot;

import java.util.List;

public class NetworkDTOs {

    public record JoinRequest(String type, String username, String password, String roomId) {}

    public record JoinAcceptedResponse(String type, String username, char color, int rating) {
        public JoinAcceptedResponse(String username, char color, int rating) {
            this("JOIN_ACCEPTED", username, color, rating);
        }
    }

    public record JoinRejectedResponse(String type, String reason) {
        public JoinRejectedResponse(String reason) {
            this("JOIN_REJECTED", reason);
        }
    }

    public record GameStartedResponse(String type, List<String> data) {
        public GameStartedResponse(String whiteUser, String blackUser) {
            this("GAME_STARTED", List.of(whiteUser, blackUser));
        }
    }

    public record SimpleEventResponse(String type, List<Object> data) {}

    public record ReconnectAcceptedResponse(String type, String username, char color, int rating) {
        public ReconnectAcceptedResponse(String username, char color, int rating) {
            this("RECONNECT_ACCEPTED", username, color, rating);
        }
    }

    public record ReconnectRejectedResponse(String type, String reason) {
        public ReconnectRejectedResponse(String reason) {
            this("RECONNECT_REJECTED", reason);
        }
    }

    public record LoginRequest(String type, String username, String password) {}

    public record LoginSuccessResponse(String type, String username, int rating) {
        public LoginSuccessResponse(String username, int rating) {
            this("LOGIN_SUCCESS", username, rating);
        }
    }

    public record LoginRejectedResponse(String type, String reason) {
        public LoginRejectedResponse(String reason) {
            this("LOGIN_REJECTED", reason);
        }
    }

    // --- Previously built ad hoc via objectMapper.writeValueAsString(Map.of(...))
    // or raw hand-escaped JSON strings inside GameRoom. Pulling them into DTOs
    // means RoomMessenger is the only place that touches JSON for these, and a
    // typo in a field name is a compile error instead of a silent client bug.

    public record BoardUpdateResponse(String type, GameSnapshot snapshot) {
        public BoardUpdateResponse(GameSnapshot snapshot) {
            this("BOARD_UPDATE", snapshot);
        }
    }

    public record GameOverResponse(String type, String winner, String reason) {
        public GameOverResponse(String winner, String reason) {
            this("GAME_OVER", winner, reason);
        }
    }

    public record DisconnectCountdownResponse(String type, int seconds, String winnerIfTimeout) {
        public DisconnectCountdownResponse(int seconds, String winnerIfTimeout) {
            this("DISCONNECT_COUNTDOWN", seconds, winnerIfTimeout);
        }
    }

    public record DisconnectCancelledResponse(String type) {
        public DisconnectCancelledResponse() {
            this("DISCONNECT_CANCELLED");
        }
    }
}
