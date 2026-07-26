package network.client;

import org.example.app.GameLifecycleManager;
import org.example.bus.GameEventBus;
import org.example.controllers.NetworkController;
import org.example.engines.GameHistoryManager;
import org.example.models.Role;
import org.example.view.BoardGeometry;
import org.example.view.ImgRenderer;
import org.example.view.ScoreManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * GameLifecycleManager is mostly Swing wiring (creates real GameWindow /
 * GameFrameComposer instances on the EDT via SwingUtilities.invokeLater),
 * which isn't practical to unit test without a headful display or a much
 * larger UI test harness. This focuses on the one piece of pure logic that
 * runs *before* any Swing object is touched: which Role a player is
 * assigned once GAME_STARTED arrives, based on currentUsername vs the
 * white/black usernames in the event payload. That assignment
 * (controller.setRole(...)) is verifiable synchronously; everything after
 * it in handleGameStarted is deferred to SwingUtilities.invokeLater and
 * intentionally left untested here.
 *
 * NetworkController, BoardGeometry, ImgRenderer, GameHistoryManager and
 * ScoreManager aren't shown in the files provided, so this assumes they
 * have simple, mockable/constructible shapes consistent with how
 * GameLifecycleManager uses them (a mockable NetworkController with a
 * setRole(Role) method; the render/geometry/history/score collaborators
 * are only stored, never called, before the Swing hand-off).
 */
class GameLifecycleManagerTest {

    private NetworkController controller;
    private GameLifecycleManager manager;

    @BeforeEach
    void setUp() {
        controller = mock(NetworkController.class);
        manager = new GameLifecycleManager(
                mock(BoardGeometry.class),
                mock(ImgRenderer.class),
                mock(GameHistoryManager.class),
                mock(ScoreManager.class),
                controller);
        manager.registerEventListeners();
    }

    @Test
    void registerEventListeners_doesNotThrow() {
        // Already invoked in setUp(); this just documents that wiring up
        // every subscription is itself safe to call.
        assertDoesNotThrow(() -> new GameLifecycleManager(
                mock(BoardGeometry.class), mock(ImgRenderer.class),
                mock(GameHistoryManager.class), mock(ScoreManager.class), controller)
                .registerEventListeners());
    }

    @Test
    void loginSuccess_updatesCurrentUsername_reflectedInSubsequentRoleAssignment() {
        manager.setCurrentUsername("someone-else");
        GameEventBus.getInstance().publish("LOGIN_SUCCESS", new Object[]{ "alice", 'W', 1200 });

        // GAME_STARTED with alice as white should now resolve to WHITE,
        // proving LOGIN_SUCCESS's subscriber updated currentUsername.
        GameEventBus.getInstance().publish("GAME_STARTED", new Object[]{ "alice", "bob" });

        verify(controller, timeout(500)).setRole(Role.WHITE);
    }

    @Test
    void gameStarted_currentUserIsWhite_assignsWhiteRole() {
        manager.setCurrentUsername("alice");

        GameEventBus.getInstance().publish("GAME_STARTED", new Object[]{ "alice", "bob" });

        verify(controller, timeout(500)).setRole(Role.WHITE);
    }

    @Test
    void gameStarted_currentUserIsBlack_assignsBlackRole() {
        manager.setCurrentUsername("bob");

        GameEventBus.getInstance().publish("GAME_STARTED", new Object[]{ "alice", "bob" });

        verify(controller, timeout(500)).setRole(Role.BLACK);
    }

    @Test
    void gameStarted_currentUserIsNeitherSeat_assignsSpectatorRole() {
        manager.setCurrentUsername("carol");

        GameEventBus.getInstance().publish("GAME_STARTED", new Object[]{ "alice", "bob" });

        verify(controller, timeout(500)).setRole(Role.SPECTATOR);
    }

    @Test
    void gameStarted_usernameComparisonIsCaseInsensitive() {
        manager.setCurrentUsername("ALICE");

        GameEventBus.getInstance().publish("GAME_STARTED", new Object[]{ "alice", "bob" });

        verify(controller, timeout(500)).setRole(Role.WHITE);
    }

    @Test
    void matchFound_updatesRoomId_andRepublishesJoinRequest() {
        java.util.concurrent.atomic.AtomicReference<Object> received = new java.util.concurrent.atomic.AtomicReference<>();
        GameEventBus.getInstance().subscribe("REQUEST_JOIN_MATCH_ROOM", received::set);

        GameEventBus.getInstance().publish("MATCH_FOUND", new Object[]{ "match_42", "bob" });

        assertEquals("match_42", received.get());
    }
}
