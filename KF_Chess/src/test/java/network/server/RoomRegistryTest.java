package network.server;

import org.example.network.server.room.GameRoom;
import org.example.network.server.room.RoomRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * NOTE: these tests instantiate real GameRoom objects (via tryCreateRoom /
 * getOrCreateRoom), which in turn build a real GameEngine/Board from
 * board.csv on the classpath. They rely on the rest of the project
 * (GameEngine, BoardLoader, RealTimeArbiter, RatingService) being present,
 * which it is in the actual repo -- just not in this standalone file set.
 */
class RoomRegistryTest {

    private RoomRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new RoomRegistry();
    }

    @Test
    void tryCreateRoom_succeedsOnce_thenReturnsNullForDuplicateId() {
        GameRoom first = registry.tryCreateRoom("room1");
        GameRoom second = registry.tryCreateRoom("room1");

        assertNotNull(first);
        assertNull(second);
        assertSame(first, registry.getRoom("room1"));
    }

    @Test
    void getOrCreateRoom_isIdempotentForSameId() {
        GameRoom first = registry.getOrCreateRoom("match1");
        GameRoom second = registry.getOrCreateRoom("match1");

        assertSame(first, second);
    }

    @Test
    void getRoom_unknownId_returnsNull() {
        assertNull(registry.getRoom("does-not-exist"));
    }

    @Test
    void bindParticipant_registersPlayerSessionAndUsernameLookups() {
        GameRoom room = registry.tryCreateRoom("room1");
        WebSocketSession session = mock(WebSocketSession.class);

        registry.bindParticipant(session, "alice", room, 'W');

        assertEquals("alice", registry.getPlayer(session).username());
        assertEquals('W', registry.getPlayer(session).color());
        assertSame(room, registry.getRoomForSession(session));
        assertSame(room, registry.getActiveRoomForUsername("alice"));
    }

    @Test
    void bindSpectator_registersPlayerAndSessionButNotUsernameLookup() {
        GameRoom room = registry.tryCreateRoom("room1");
        WebSocketSession session = mock(WebSocketSession.class);

        registry.bindSpectator(session, "carol", room);

        assertEquals("carol", registry.getPlayer(session).username());
        assertEquals('-', registry.getPlayer(session).color());
        assertSame(room, registry.getRoomForSession(session));
        assertNull(registry.getActiveRoomForUsername("carol"));
    }

    @Test
    void registerPlayerInfo_setsPlayerWithoutBindingRoom() {
        WebSocketSession session = mock(WebSocketSession.class);

        registry.registerPlayerInfo(session, "dave", 'W');

        assertEquals("dave", registry.getPlayer(session).username());
        assertNull(registry.getRoomForSession(session));
    }

    @Test
    void getActiveRoomForUsername_returnsNull_whenNeverBound() {
        assertNull(registry.getActiveRoomForUsername("ghost"));
    }

    @Test
    void dropSession_removesBookkeeping_andReturnsRoom() {
        GameRoom room = registry.tryCreateRoom("room1");
        WebSocketSession session = mock(WebSocketSession.class);
        registry.bindParticipant(session, "alice", room, 'W');

        GameRoom dropped = registry.dropSession(session);

        assertSame(room, dropped);
        assertNull(registry.getPlayer(session));
        assertNull(registry.getRoomForSession(session));
        // usernameToRoom entry is untouched by dropSession by design --
        // it's cleaned up either by unregisterRoomIfAbandoned or the
        // room's onEnded callback, so a reconnect can still find it.
        assertSame(room, registry.getActiveRoomForUsername("alice"));
    }

    @Test
    void dropSession_unknownSession_returnsNull() {
        assertNull(registry.dropSession(mock(WebSocketSession.class)));
    }

    @Test
    void unregisterRoomIfAbandoned_removesRoomAndUsernameEntries() {
        GameRoom room = registry.tryCreateRoom("room1");
        WebSocketSession session = mock(WebSocketSession.class);
        registry.bindParticipant(session, "alice", room, 'W');

        registry.unregisterRoomIfAbandoned(room);

        assertNull(registry.getRoom("room1"));
        assertNull(registry.getActiveRoomForUsername("alice"));
    }

    @Test
    void wireCleanupOnEnd_removesRoomWhenGameEnds() {
        GameRoom room = registry.tryCreateRoom("room1");
        WebSocketSession white = mock(WebSocketSession.class);
        WebSocketSession black = mock(WebSocketSession.class);
        room.addPlayer(white, "alice");
        registry.bindParticipant(white, "alice", room, 'W');
        room.addPlayer(black, "bob");
        registry.bindParticipant(black, "bob", room, 'B');

        room.endGame("alice", "bob", "RESIGN_DISCONNECT");

        assertNull(registry.getRoom("room1"));
        assertNull(registry.getActiveRoomForUsername("alice"));
        assertNull(registry.getActiveRoomForUsername("bob"));
    }
}
