package org.example.view;

import java.io.BufferedReader;
import java.io.FileReader;

public class ConfigParser {

    public static void parseJson(String path, AnimationConfig config) {
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line.trim());
            }
            String json = sb.toString().replace(" ", "").replace("\"", "");

            if (json.contains("speed_m_per_sec"))
                config.setSpeedMPerSec(Double.parseDouble(extractValue(json, "speed_m_per_sec")));
            if (json.contains("next_state_when_finished"))
                config.setNextStateWhenFinished(extractValue(json, "next_state_when_finished"));
            if (json.contains("frames_per_sec")) {
                int fps = Integer.parseInt(extractValue(json, "frames_per_sec"));
                if (fps > 0) config.setFrameDuration(1000 / fps);
            }
            if (json.contains("is_loop"))
                config.setLoop(Boolean.parseBoolean(extractValue(json, "is_loop")));
            if (json.contains("duration_ms"))
                config.setDurationMs(Long.parseLong(extractValue(json, "duration_ms")));

        } catch (Exception ignored) {
            // כשל בטעינה ישאיר ערכי ברירת מחדל בטוחים
        }
    }

    private static String extractValue(String json, String key) {
        int keyIndex = json.indexOf(key);
        int colonIndex = json.indexOf(":", keyIndex);
        int commaIndex = json.indexOf(",", colonIndex);
        int closeBraceIndex = json.indexOf("}", colonIndex);

        if (commaIndex == -1 || commaIndex > closeBraceIndex) {
            commaIndex = closeBraceIndex;
        }
        return json.substring(colonIndex + 1, commaIndex).trim();
    }
}