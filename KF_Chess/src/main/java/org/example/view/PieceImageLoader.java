package org.example.view;

import org.example.Img;

import org.example.models.State;
import java.awt.Dimension;
import java.util.HashMap;
import java.util.Map;
import java.io.InputStream;


public class PieceImageLoader {
    // מפתח המטמון: "COLOR_TYPE_STATE" (למשל: "b_Q_IDLE")
    private final Map<String, Img> piecesCache = new HashMap<>();
    private final int cellSize;

    public PieceImageLoader(int cellSize) {
        this.cellSize = cellSize;
    }

    /**
     * טעינה מראש של כל הכלים מתוך תיקיית resources
     */
    public void preload() {
        Dimension targetSize = new Dimension(cellSize - 20, cellSize - 20);

        char[] types = {'P', 'R', 'N', 'B', 'Q', 'K'};
        char[] colors = {'w', 'b'};
        State[] states = {State.IDLE, State.JUMPING}; // הוסיפי עוד מצבים מה-Enum שלך במידה ויש

        for (char color : colors) {
            // התאמה לתיקיות שלך: 'W' עבור לבן, 'B' עבור שחור
            char colorUpper = Character.toUpperCase(color);

            for (char type : types) {
                // שם תיקיית הכלי, למשל "QW" (מלכה לבנה) או "KB" (מלך שחור)
                String pieceDirName = "" + Character.toUpperCase(type) + colorUpper;

                for (State state : states) {
                    String stateDirName = mapStateToFolderName(state);

                    // נתיב יחסי בתוך תיקיית ה-resources (מתחיל ב-/)
                    String resourcePath = "/pieces/" + pieceDirName + "/states/" + stateDirName + "/sprites/1.png";

                    String cacheKey = color + "_" + Character.toUpperCase(type) + "_" + state;

                    try {
                        // טעינת הקובץ מתוך ה-resources (עובד מעולה גם כשמייצאים ל-JAR!)
                        Img img = loadFromResources(resourcePath, targetSize);
                        if (img != null) {
                            piecesCache.put(cacheKey, img);
                        }
                    } catch (Exception e) {
                        // אם אין תמונה ספציפית למצב הנוכחי, ננסה לטעון את ה-idle כגיבוי
                        if (state != State.IDLE) {
                            String fallbackPath = "/pieces/" + pieceDirName + "/states/idle/sprites/1.png";
                            try {
                                Img fallbackImg = loadFromResources(fallbackPath, targetSize);
                                if (fallbackImg != null) {
                                    piecesCache.put(cacheKey, fallbackImg);
                                }
                            } catch (Exception ignored) {}
                        } else {
                            System.err.println("Warning: Could not load resource: " + resourcePath);
                        }
                    }
                }
            }
        }
    }

    public Img getPieceImage(char color, char type, State state) {
        String cacheKey = color + "_" + Character.toUpperCase(type) + "_" + state;
        return piecesCache.get(cacheKey);
    }

    private String mapStateToFolderName(State state) {
        switch (state) {
            case JUMPING:
                return "jump";
            case IDLE:
            default:
                return "idle";
        }
    }

    /**
     * מתודת עזר שטוענת קובץ תמונה ישירות מתוך ה-resources ומחזירה אובייקט Img מותאם גודל
     */
    private Img loadFromResources(String path, Dimension targetSize) {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                return null;
            }
            // קורא את התמונה לתוך Img
            Img img = new Img();

            // מכיוון שבמחלקה Img שלך הפונקציה read מקבלת נתיב כ-String וקוראת מ-File,
            // נבצע התאמה קלה כאן או לחלופין נטען את התמונה באמצעות ImageIO ונזין אותה.
            // הדרך הפשוטה ביותר ללא שינוי Img היא פשוט להעביר את הנתיב הפיזי המלא בתוך הפרויקט:
            String systemPath = "src/main/resources" + path;
            return new Img().read(systemPath, targetSize, true, null);
        } catch (Exception e) {
            return null;
        }
    }
}