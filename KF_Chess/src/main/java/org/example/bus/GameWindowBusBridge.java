package org.example.bus;

import org.example.bus.GameEventBus;
import org.example.view.GameFrameComposer;
import org.example.view.GameWindow;
import org.example.view.Img;

/**
 * The only job of this class: listen on the bus, call the window's and
 * composer's existing public methods. Neither GameWindow.java nor
 * GameFrameComposer.java are modified for this - they already expose
 * updateFrame(Img) / updateBoardOffsets(x, y) and getBoardX()/getBoardY().
 *
 * Create exactly one of these in your composition root, after GameWindow,
 * GameFrameComposer and the bus all exist:
 *
 *   GameWindow window = new GameWindow(...);
 *   window.init(controller);
 *   GameFrameComposer composer = new GameFrameComposer(...);
 *   new GameWindowBusBridge(window, composer);
 */
public class GameWindowBusBridge {

    public GameWindowBusBridge(GameWindow window, GameFrameComposer composer) {
        GameEventBus.getInstance().subscribe("FRAME_READY", data -> {
            Img frame = (Img) data;
            // The composer just finished composing this frame (that's what
            // triggered FRAME_READY), so its board offsets are fresh.
            window.updateBoardOffsets(composer.getBoardX(), composer.getBoardY());
            window.updateFrame(frame);
        });
    }
}

