package org.example.app;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.example.bus.GameEventBus;
import org.example.bus.GameWindowBusBridge;
import org.example.controllers.NetworkController;
import org.example.engines.GameHistoryManager;
import org.example.engines.GameSnapshot;
import org.example.models.Role;
import org.example.view.*;

import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class GameLifecycleManager {

    private final BoardGeometry geometry;
    private final ImgRenderer boardRenderer;
    private final GameHistoryManager historyManager;
    private final ScoreManager scoreManager;
    private final NetworkController controller;

    private static volatile GameWindow activeWindow = null;
    private final AtomicBoolean isRendering = new AtomicBoolean(false);

    @Setter
    private String currentUsername;
    @Setter
    private String currentRoomId;

    public GameLifecycleManager(
            BoardGeometry geometry,
            ImgRenderer boardRenderer,
            GameHistoryManager historyManager,
            ScoreManager scoreManager,
            NetworkController controller) {
        this.geometry = geometry;
        this.boardRenderer = boardRenderer;
        this.historyManager = historyManager;
        this.scoreManager = scoreManager;
        this.controller = controller;
    }

    public void registerEventListeners() {
        GameEventBus eventBus = GameEventBus.getInstance();

        // 🔊 Sound effects. Kept separate from the window/rendering
        // subscriptions below so audio logic doesn't get tangled up with UI
        // logic -- if you want to mute, add a volume slider, etc., this is
        // the only place to touch.
        eventBus.subscribe("GAME_STARTED", data -> SoundManager.play(SoundManager.Sound.GAME_START));
        eventBus.subscribe("PIECE_CAPTURED", data -> SoundManager.play(SoundManager.Sound.PIECE_CAPTURED));
        eventBus.subscribe("GAME_OVER", data -> SoundManager.play(SoundManager.Sound.GAME_OVER));

        eventBus.subscribe("JOIN_ACCEPTED", data ->
                log.info("[CLIENT OUT] JOIN_ACCEPTED received from server: {}", data)
        );

        eventBus.subscribe("MATCH_FOUND", data -> {
            Object[] matchPayload = (Object[]) data;
            String assignedRoomId = (String) matchPayload[0];
            String opponent = (String) matchPayload[1];

            this.currentRoomId = assignedRoomId;

            eventBus.publish("REQUEST_JOIN_MATCH_ROOM", assignedRoomId);
        });

        eventBus.subscribe("LOGIN_SUCCESS", data -> {
            Object[] payload = (Object[]) data;
            String loggedInUser = (String) payload[0];
            this.currentUsername = loggedInUser;
            log.info("[CLIENT OUT] LOGIN_SUCCESS received. Updated current user: {}", loggedInUser);
        });

        eventBus.subscribe("BOARD_UPDATE_RECEIVED", rawSnapshot -> {
            GameSnapshot snapshot = (GameSnapshot) rawSnapshot;
            controller.updateSnapshot(snapshot);

            GameWindow window = activeWindow;
            if (window != null && isRendering.compareAndSet(false, true)) {
                SwingUtilities.invokeLater(() -> {
                    try {
                        GameFrameComposer.BoardUpdatePayload payload = new GameFrameComposer.BoardUpdatePayload(
                                snapshot,
                                window.getWidth(),
                                window.getHeight()
                        );
                        eventBus.publish("BOARD_UPDATE", payload);
                    } finally {
                        isRendering.set(false);
                    }
                });
            }
        });

        eventBus.subscribe("GAME_STARTED", this::handleGameStarted);
    }

    private void handleGameStarted(Object data) {
        Object[] players = (Object[]) data;
        String whiteUser = (String) players[0];
        String blackUser = (String) players[1];

        log.info("[CLIENT OUT] GAME_STARTED received. White: {} | Black: {}", whiteUser, blackUser);

        Role userRole;
        if (currentUsername != null && currentUsername.equalsIgnoreCase(whiteUser)) {
            userRole = Role.WHITE;
        } else if (currentUsername != null && currentUsername.equalsIgnoreCase(blackUser)) {
            userRole = Role.BLACK;
        } else {
            userRole = Role.SPECTATOR;
        }

        controller.setRole(userRole);

        SwingUtilities.invokeLater(() -> {
            if (activeWindow != null) return;

            GameWindow window = new GameWindow("KF Chess - Room: " + (currentRoomId != null ? currentRoomId : "Main"), 1400, 780, geometry);
            activeWindow = window;

            window.init(controller);
            window.updateRole(userRole);

            GameFrameComposer composer = new GameFrameComposer(boardRenderer, historyManager, geometry, scoreManager, whiteUser, blackUser);
            new GameWindowBusBridge(window, composer);

            if (controller.getLatestSnapshot() != null) {
                GameFrameComposer.BoardUpdatePayload payload = new GameFrameComposer.BoardUpdatePayload(
                        controller.getLatestSnapshot(),
                        window.getWidth(),
                        window.getHeight()
                );
                GameEventBus.getInstance().publish("BOARD_UPDATE", payload);
            }
        });
    }
}