package org.example.view;

/**
 * מידע תצוגתי על שחקן - שם וניקוד - המוצג מעל/מתחת ללוח.
 * לא קשור ללוגיקת המשחק עצמה, רק לתצוגה.
 */
public class PlayerInfo {
    public final String name;
    public int score;

    public PlayerInfo(String name) {
        this(name, 0);
    }

    public PlayerInfo(String name, int score) {
        this.name = name;
        this.score = score;
    }
}
