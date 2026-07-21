package org.example.controllers;

import org.example.engines.GameSnapshot;


/**
 * ממשק אחוד לניהול קלט השחקן מהלוח (לחיצות וקפיצות).
 */
public interface ChessController {

    /**
     * טיפול בלחיצה על משבצת בלוח.
     * @param col אינדקס עמודה (0-7)
     * @param row אינדקס שורה (0-7)
     */
    void click(int col, int row);

    /**
     * טיפול בפעולת קפיצה עבור משבצת בלוח.
     */
    void jump(int col, int row);

    /**
     * איפוס הבחירה הנוכחית בלוח.
     */
    void clearSelection();

    /**
     * עדכון תמונת המצב העדכנית (משמש בעיקר במוד רשת).
     * ברירת מחדל ריקה כדי שמוד מקומי לא יידרש לממש אותה.
     */
    default void updateSnapshot(GameSnapshot snapshot) {
        // No-op for local mode
    }
}