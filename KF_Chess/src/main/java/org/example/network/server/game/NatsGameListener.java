package org.example.network.server.game;

import lombok.extern.slf4j.Slf4j;
import org.example.network.nats.NatsBridge;
import org.example.network.server.room.GameRoom;
import org.example.network.server.room.RoomRegistry;
import org.springframework.stereotype.Component;

/**
 * Fixed wiring bug: this used to be a static-only class with a {@code
 * startListening(RoomRegistry)} method that nothing in the provided
 * codebase ever called -- so {@code game.events.*} had no subscriber at
 * runtime despite the class existing and looking complete. Converted to
 * a normal Spring {@code @Component} that subscribes in its constructor,
 * the same pattern already used by {@code AuthNatsSubscriber}, {@code
 * GameCommandNatsSubscriber}, and {@code ChessWebSocketHandler} -- Spring
 * now instantiates and wires this automatically via component scanning,
 * no manual startup call needed anywhere.
 */
@Slf4j
@Component
public class NatsGameListener {

    private final RoomRegistry roomRegistry;

    public NatsGameListener(RoomRegistry roomRegistry) {
        this.roomRegistry = roomRegistry;

        NatsBridge.subscribe("game.events.*", (subject, rawJson) -> {
            String roomId = extractRoomIdFromSubject(subject);
            GameRoom localRoom = roomRegistry.getRoom(roomId);

            if (localRoom != null) {
                localRoom.handleIncomingPayload(rawJson);
            }
        });
    }

    private static String extractRoomIdFromSubject(String subject) {
        return subject.substring(subject.lastIndexOf('.') + 1);
    }
}
