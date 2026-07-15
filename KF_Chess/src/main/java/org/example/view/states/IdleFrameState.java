package org.example.view.states;

import org.example.Img;
import org.example.view.AnimationConfig;

public class IdleFrameState implements PieceFrameState {
    @Override
    public String getFolderName() {
        return "idle";
    }

    @Override
    public Img getFrame(AnimationConfig config, long startTime, long currentTime) {
        if (config == null || config.frames.isEmpty()) return null;

        long elapsed = currentTime - startTime;
        int frameIndex = (int) (elapsed / config.frameDuration);

        // במצב Idle תמיד נרצה לעשות לופ (גם אם מוגדר אחרת, לביטחון)
        int actualIndex = frameIndex % config.frames.size();
        return config.frames.get(actualIndex);
    }
}