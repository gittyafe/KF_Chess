package org.example.view;

import org.example.Img;
import java.util.ArrayList;
import java.util.List;

public class AnimationConfig {
    public List<Img> frames = new ArrayList<>();

    // גרפיקה (graphics)
    public int frameDuration = 100; // מחושב מתוך frames_per_sec
    public boolean loop = true;     //

    // פיזיקה (physics)
    public double speedMPerSec = 0.0;
    public String nextStateWhenFinished = "idle";

    /**
     * מחזירה את משך הזמן הכולל של האנימציה הזו במילישניות
     */
    public long getTotalDuration() {
        return (long) frames.size() * frameDuration;
    }
}