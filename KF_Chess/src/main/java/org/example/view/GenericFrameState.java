package org.example.view;

import org.example.Img;
import org.example.view.AnimationConfig;

public class GenericFrameState {
    private final String folderName;

    public GenericFrameState(String folderName) {
        this.folderName = folderName;
    }


    public String getFolderName() {
        return this.folderName;
    }


    public Img getFrame(AnimationConfig config, long startTime, long currentTime) {
        if (config == null || config.frames.isEmpty()) return null;

        long elapsed = currentTime - startTime;
        int frameIndex = (int) (elapsed / config.frameDuration);

        // אם מוגדר ב-JSON שלא לבצע לופ והאנימציה הסתיימה, נשארים על הפריים האחרון
        if (!config.loop && frameIndex >= config.frames.size()) {
            return config.frames.get(config.frames.size() - 1);
        }

        int actualIndex = config.loop ? (frameIndex % config.frames.size()) : Math.min(frameIndex, config.frames.size() - 1);
        return config.frames.get(actualIndex);
    }
}