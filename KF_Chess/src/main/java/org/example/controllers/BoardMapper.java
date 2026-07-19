package org.example.controllers;

/**
 * @deprecated זהו מקור הבאג: {@code PIXELS_PER_CELL} היה קבוע קשיח (81),
 * בעוד שגודל המשבצת האמיתי משתנה דינמית עם גודל החלון (ר' BoardGeometry).
 * ההמרה פיקסל -> משבצת עברה כעת ל-{@code BoardGeometry#columnAt} /
 * {@code BoardGeometry#rowAt}, שקוראים תמיד את הגודל העדכני בפועל.
 * ניתן למחוק את המחלקה הזו לגמרי לאחר שוידאת שאין עוד קריאות אליה בקוד.
 */
@Deprecated
public class BoardMapper {
    private static final int PIXELS_PER_CELL = 81;

    @Deprecated
    public static int pixelToCell(int pixel) {
        return pixel / PIXELS_PER_CELL;
    }
}
