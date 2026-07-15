package org.example.view.states;

import org.example.Img;
import org.example.view.AnimationConfig;

public class MovingFrameState implements PieceFrameState {
    @Override
    public String getFolderName() {
        return "move";
    }

    @Override
    public Img getFrame(AnimationConfig config, long startTime, long currentTime) {
        if (config == null || config.frames.isEmpty()) return null;

        long elapsed = currentTime - startTime;
        int frameIndex = (int) (elapsed / config.frameDuration);

        if (!config.loop && frameIndex >= config.frames.size()) {
            return config.frames.get(config.frames.size() - 1); // החזק פריים אחרון
        }

        int actualIndex = config.loop ? (frameIndex % config.frames.size()) : Math.min(frameIndex, config.frames.size() - 1);
        return config.frames.get(actualIndex);
    }
}