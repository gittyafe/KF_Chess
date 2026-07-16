package org.example.view;

/**
 * קבועי פריסה כלליים המשותפים בין הרכבת הפריים (GameFrameComposer)
 * לבין חלון התצוגה (Main / GameWindow).
 *
 * לפני התיקון: MASTER_WIDTH/HEIGHT הוגדרו בתוך GameFrameComposer (1400x950),
 * בעוד ש-Main יצר GameWindow בגודל 1400x1000 בנפרד - שני "מקורות אמת"
 * שלא תואמים. עכשיו יש מקום אחד בלבד לגודל המסגרת הכוללת.
 */
public final class DisplayConstants {

    private DisplayConstants() {
        // מחלקת קבועים - לא ניתנת ליצירת מופע
    }

    public static final int MASTER_WIDTH = 1400;
    public static final int MASTER_HEIGHT = 950;
}
