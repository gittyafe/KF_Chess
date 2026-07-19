package org.example.view;

import org.example.Img;
import java.util.ArrayList;
import java.util.List;

public class AnimationConfig {
    private final List<Img> frames = new ArrayList<>();
    private int frameDuration = 100;
    private boolean loop = true;
    private double speedMPerSec = 0.0;
    private String nextStateWhenFinished = "idle";
    private long durationMs = 0;

    public List<Img> getFrames() { return frames; }
    public int getFrameDuration() { return frameDuration; }
    public boolean isLoop() { return loop; }
    public double getSpeedMPerSec() { return speedMPerSec; }
    public String getNextStateWhenFinished() { return nextStateWhenFinished; }
    public long getDurationMs() { return durationMs; }

    public void setFrameDuration(int frameDuration) { this.frameDuration = frameDuration; }
    public void setLoop(boolean loop) { this.loop = loop; }
    public void setSpeedMPerSec(double speedMPerSec) { this.speedMPerSec = speedMPerSec; }
    public void setNextStateWhenFinished(String nextState) { this.nextStateWhenFinished = nextState; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public long getTotalDuration() {
        return durationMs > 0 ? durationMs : (long) frames.size() * frameDuration;
    }

    /**
     * מחזירה את הפריים המדויק לזמן הנתון מבלי לייצר אובייקטי מצב זמניים
     */
    public Img getCurrentFrame(long startTime, long currentTime) {
        if (frames.isEmpty()) return null;

        long elapsed = currentTime - startTime;
        int frameIndex = (int) (elapsed / frameDuration);

        if (!loop && frameIndex >= frames.size()) {
            return frames.get(frames.size() - 1);
        }

        int actualIndex = loop ? (frameIndex % frames.size()) : Math.min(frameIndex, frames.size() - 1);
        return frames.get(actualIndex);
    }

    public void setTotalDuration(long totalDuration) {
        this.durationMs = totalDuration; // ודאי שזה שם השדה אצלך במחלקה (למשל durationMs או totalDuration)
    }

    public void setFrames(java.util.List<Img> imgs) {
        this.frames.clear();
        if (imgs != null) {
            this.frames.addAll(imgs);
        }
    }
}