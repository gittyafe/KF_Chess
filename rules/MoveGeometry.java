package rules;

import models.Position;

public class MoveGeometry {

    public static boolean isDirectionValid(char type, Position src, Position dest, char color, boolean isCapture, int boardHeight) {
        int dr = Math.abs(dest.getRow() - src.getRow());
        int dc = Math.abs(dest.getColumn() - src.getColumn());

        return switch (Character.toUpperCase(type)) {
            case 'R' -> isRookGeometry(src, dest);
            case 'B' -> isBishopGeometry(dr, dc);
            case 'Q' -> isRookGeometry(src, dest) || isBishopGeometry(dr, dc);
            case 'K' -> (dr <= 1 && dc <= 1);
            case 'N' -> (dr == 1 && dc == 2) || (dr == 2 && dc == 1);
            case 'P' -> isPawnGeometry(src, dest, dr, dc, color, isCapture, boardHeight);
            default  -> false;
        };
    }

    // קריא, קצר ופשוט - צריח נע בקו ישר
    private static boolean isRookGeometry(Position src, Position dest) {
        return src.getRow() == dest.getRow() || src.getColumn() == dest.getColumn();
    }

    // קריא, קצר ופשוט - רץ נע באלכסון מושלם
    private static boolean isBishopGeometry(int dr, int dc) {
        return dr == dc;
    }

    // הלוגיקה המורכבת היחידה מבודדת כאן, בלי ללכלך את ה-switch הראשי
    private static boolean isPawnGeometry(Position src, Position dest, int dr, int dc, char color, boolean isCapture, int boardHeight) {
        int requiredDir = (color == 'w') ? -1 : 1;
        int actualDir = dest.getRow() - src.getRow();
        int startRank = (color == 'w') ? boardHeight - 2 : 1;

        // תנועה ישרה קדימה
        if (src.getColumn() == dest.getColumn()) {
            if (isCapture) return false; // רגלי לא מכה קדימה
            return (actualDir == requiredDir) || (actualDir == 2 * requiredDir && src.getRow() == startRank);
        }
        
        // הכאה באלכסון
        return (dc == 1 && actualDir == requiredDir && isCapture);
    }
}
