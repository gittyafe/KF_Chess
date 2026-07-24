package org.example.view;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.net.URL;
import java.util.EnumMap;
import java.util.Map;

/**
 * Plays short sound effects for game events (game start, piece captured,
 * game over).
 *
 * Looks for a real .wav file on the classpath first; if one isn't there,
 * it synthesizes a simple placeholder beep instead, so sound works right
 * away even before you've added real audio assets.
 *
 * To use real sounds, just drop .wav files at:
 *   src/main/resources/sounds/game_start.wav
 *   src/main/resources/sounds/capture.wav
 *   src/main/resources/sounds/game_over.wav
 * No code changes needed -- the placeholder tone is only used when the
 * corresponding file is missing.
 */
public class SoundManager {

    public enum Sound {
        GAME_START("/sounds/game_start.wav", 660, 140),
        PIECE_CAPTURED("/sounds/capture.wav", 220, 90),
        GAME_OVER("/sounds/game_over.wav", 440, 500);

        final String resourcePath;
        final int placeholderFrequencyHz;
        final int placeholderDurationMs;

        Sound(String resourcePath, int placeholderFrequencyHz, int placeholderDurationMs) {
            this.resourcePath = resourcePath;
            this.placeholderFrequencyHz = placeholderFrequencyHz;
            this.placeholderDurationMs = placeholderDurationMs;
        }
    }

    private static final Map<Sound, Clip> clips = new EnumMap<>(Sound.class);
    private static volatile boolean muted = false;

    static {
        for (Sound sound : Sound.values()) {
            try {
                clips.put(sound, loadClip(sound));
            } catch (Exception e) {
                System.err.println("Could not prepare sound for " + sound + ": " + e.getMessage());
            }
        }
    }

    private static Clip loadClip(Sound sound) throws Exception {
        URL url = SoundManager.class.getResource(sound.resourcePath);
        AudioInputStream audioIn = (url != null)
                ? AudioSystem.getAudioInputStream(url)
                : generatePlaceholderTone(sound.placeholderFrequencyHz, sound.placeholderDurationMs);

        Clip clip = AudioSystem.getClip();
        clip.open(audioIn);
        return clip;
    }

    /** Synthesizes a simple sine-wave beep as a stand-in until a real .wav exists. */
    private static AudioInputStream generatePlaceholderTone(int frequencyHz, int durationMs) {
        float sampleRate = 44100f;
        int numSamples = (int) (sampleRate * durationMs / 1000.0);
        byte[] buffer = new byte[numSamples * 2]; // 16-bit mono, little-endian

        for (int i = 0; i < numSamples; i++) {
            // Fade out over the last 20% so it doesn't end in an audible click.
            double fade = Math.min(1.0, (numSamples - i) / (numSamples * 0.2));
            double angle = 2.0 * Math.PI * i * frequencyHz / sampleRate;
            short sample = (short) (Math.sin(angle) * Short.MAX_VALUE * 0.5 * fade);
            buffer[i * 2] = (byte) (sample & 0xFF);
            buffer[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }

        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        return new AudioInputStream(new ByteArrayInputStream(buffer), format, numSamples);
    }

    /** Plays the given sound from the start, cutting off any previous
     *  playback of that same sound (e.g. a rapid string of captures). */
    public static void play(Sound sound) {
        if (muted) return;
        Clip clip = clips.get(sound);
        if (clip == null) return;

        synchronized (clip) {
            clip.stop();
            clip.setFramePosition(0);
            clip.start();
        }
    }

    public static void setMuted(boolean value) {
        muted = value;
    }

    public static boolean isMuted() {
        return muted;
    }
}
