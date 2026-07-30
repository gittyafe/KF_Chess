package org.example.bus;

import org.example.view.GameFrameComposer;
import org.example.view.GameWindow;
import org.example.view.Img;

import java.util.function.Consumer;

public class GameWindowBusBridge {

    private final Consumer<Object> onFrameReady;
    private final Consumer<Object> onGameOver;

    public GameWindowBusBridge(GameWindow window, GameFrameComposer composer) {
        this.onFrameReady = data -> {
            Img frame = (Img) data;
            window.updateBoardOffsets(composer.getBoardX(), composer.getBoardY());
            window.updateFrame(frame);
        };

        // Detaches this bridge once the game ends, so it stops holding
        // window/composer reachable through GameEventBus's listener list
        // forever. Without this, one bridge -- and the window + composer
        // it closes over -- leaks per game played in a session, even
        // though the composer's own render timer has already stopped by
        // this point (GameFrameComposer self-unsubscribes on GAME_OVER
        // too), because this subscription lives independently of that one.
        this.onGameOver = data -> shutdown();

        GameEventBus.getInstance().subscribe(GameServerEvents.FRAME_READY, onFrameReady);
        GameEventBus.getInstance().subscribe(GameServerEvents.GAME_OVER, onGameOver);
    }

    public void shutdown() {
        GameEventBus.getInstance().unsubscribe(GameServerEvents.FRAME_READY, onFrameReady);
        GameEventBus.getInstance().unsubscribe(GameServerEvents.GAME_OVER, onGameOver);
    }
}