package org.example.network;

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
}