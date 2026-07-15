package org.example.view.states;

import org.example.Img;
import org.example.view.AnimationConfig;

public interface PieceFrameState {
    String getFolderName();
    Img getFrame(AnimationConfig config, long startTime, long currentTime);
}