package org.example.view;

import java.io.BufferedReader;
import java.io.FileReader;

public class ConfigParser {

    public static void parseJson(String path, AnimationConfig config) {
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line.trim());
            }
            // הסרת רווחים וגרשיים כדי לפשט את הניתוח הטקסטואלי
            String json = jsonBuilder.toString().replace(" ", "").replace("\"", "").replace("\n", "").replace("\r", "");

            // חילוץ ערכי פיזיקה (physics)
            if (json.contains("speed_m_per_sec")) {
                config.speedMPerSec = Double.parseDouble(extractValue(json, "speed_m_per_sec"));
            }
            if (json.contains("next_state_when_finished")) {
                config.nextStateWhenFinished = extractValue(json, "next_state_when_finished");
            }

            // חילוץ ערכי גרפיקה (graphics)
            if (json.contains("frames_per_sec")) {
                int fps = Integer.parseInt(extractValue(json, "frames_per_sec"));
                if (fps > 0) {
                    config.frameDuration = 1000 / fps; // המרה מ-FPS למילישניות לפריים
                }
            }
            if (json.contains("is_loop")) {
                config.loop = Boolean.parseBoolean(extractValue(json, "is_loop"));
            }
            
            // חילוץ משך הstateבמילישניות (אם מוגדר)
            if (json.contains("duration_ms")) {
                config.durationMs = Long.parseLong(extractValue(json, "duration_ms"));
            }
        } catch (Exception e) {
            // אם הקובץ לא קיים, השדות של config יישארו עם ערכי ברירת המחדל שלהם
        }
    }

    private static String extractValue(String json, String key) {
        int keyIndex = json.indexOf(key);
        int colonIndex = json.indexOf(":", keyIndex);
        int commaIndex = json.indexOf(",", colonIndex);
        // אם מדובר באיבר האחרון באובייקט המוכל (לפני סגירת סוגריים מסולסלים)
        if (commaIndex == -1 || commaIndex > json.indexOf("}", colonIndex)) {
            commaIndex = json.indexOf("}", colonIndex);
        }
        return json.substring(colonIndex + 1, commaIndex).trim();
    }
}