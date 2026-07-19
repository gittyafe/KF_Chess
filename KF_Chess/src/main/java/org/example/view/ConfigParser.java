package org.example.view;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads a per-state {@code config.json} file (frame rate, loop flag, speed,
 * next-state-when-finished, duration) into an {@link AnimationConfig}.
 *
 * <p>Previously this stripped all quotes/spaces from the whole file and used
 * {@code indexOf(key)} to locate each value - see {@link MiniJson} for why
 * that was fragile. This version parses proper JSON and reads named fields
 * out of the resulting map.</p>
 */
public class ConfigParser {
    private static final Logger LOGGER = Logger.getLogger(ConfigParser.class.getName());

    public static void parseJson(String path, AnimationConfig config) {
        String content;
        try {
            content = Files.readString(Path.of(path));
        } catch (IOException e) {
            // Missing/unreadable config is expected for states without overrides;
            // AnimationConfig's defaults are safe to fall back on.
            return;
        }

        Map<String, Object> json;
        try {
            json = MiniJson.parseObject(content);
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Malformed animation config at " + path + " - using defaults", e);
            return;
        }

        readDouble(json, "speed_m_per_sec").ifPresent(config::setSpeedMPerSec);
        readString(json, "next_state_when_finished").ifPresent(config::setNextStateWhenFinished);
        readInt(json, "frames_per_sec").ifPresent(fps -> {
            if (fps > 0) config.setFrameDuration(1000 / fps);
        });
        readBoolean(json, "is_loop").ifPresent(config::setLoop);
        readLong(json, "duration_ms").ifPresent(config::setDurationMs);
    }

    private static java.util.Optional<Double> readDouble(Map<String, Object> json, String key) {
        Object v = json.get(key);
        return v instanceof Number n ? java.util.Optional.of(n.doubleValue()) : java.util.Optional.empty();
    }

    private static java.util.Optional<Integer> readInt(Map<String, Object> json, String key) {
        Object v = json.get(key);
        return v instanceof Number n ? java.util.Optional.of(n.intValue()) : java.util.Optional.empty();
    }

    private static java.util.Optional<Long> readLong(Map<String, Object> json, String key) {
        Object v = json.get(key);
        return v instanceof Number n ? java.util.Optional.of(n.longValue()) : java.util.Optional.empty();
    }

    private static java.util.Optional<String> readString(Map<String, Object> json, String key) {
        Object v = json.get(key);
        return v instanceof String s ? java.util.Optional.of(s) : java.util.Optional.empty();
    }

    private static java.util.Optional<Boolean> readBoolean(Map<String, Object> json, String key) {
        Object v = json.get(key);
        return v instanceof Boolean b ? java.util.Optional.of(b) : java.util.Optional.empty();
    }
}
