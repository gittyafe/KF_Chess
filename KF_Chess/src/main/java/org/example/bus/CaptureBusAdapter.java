package org.example.bus;

import org.example.bus.GameEventBus;
import org.example.engines.CaptureListener;

/**
 * Implements the CaptureListener interface GameEngine already exposes.
 * GameEngine.java is not modified - you just register an instance of this
 * via gameEngine.addCaptureListener(new CaptureBusAdapter()) in your
 * composition root (wherever GameEngine is constructed).
 *
 * The (capturedType, capturingColor) shape matches EXACTLY what
 * GameFrameComposer's "PIECE_CAPTURED" subscriber already expects
 * (it casts data to Object[] and reads [0]=capturedType, [1]=capturingColor).
 */
public class CaptureBusAdapter implements CaptureListener {
    @Override
    public void onPieceCaptured(char capturedType, char capturingColor) {
        GameEventBus.getInstance().publish("PIECE_CAPTURED", new Object[]{capturedType, capturingColor});
    }
}
