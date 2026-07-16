package org.example.view;

/**
 * מאזין ומנהל הניקוד של המשחק (מקור האמת הבלעדי לניקוד).
 * שומר על הניקוד של שני הצדדים ומעדכן אותו בכל אכילה.
 */
public class ScoreManager implements CaptureListener {
    private int whiteScore = 0;
    private int blackScore = 0;

    public int getWhiteScore() {
        return whiteScore;
    }

    public int getBlackScore() {
        return blackScore;
    }

    @Override
    public void onPieceCaptured(char capturedType, char capturingColor) {
        int points = getValue(capturedType);
        if (points <= 0) {
            return;
        }

        // שימוש ב-Character.toLowerCase כדי למנוע באגים של אותיות רישיות/קטנות מהמנוע
        char color = Character.toLowerCase(capturingColor);
        if (color == 'w') {
            whiteScore += points;
        } else if (color == 'b') {
            blackScore += points;
        }
    }

    private int getValue(char pieceType) {
        pieceType = Character.toLowerCase(pieceType);
        switch (pieceType) {
            case 'p': return 1;   // רגלי
            case 'n': return 3;   // פרש
            case 'b': return 3;   // רץ
            case 'r': return 5;   // צריח
            case 'q': return 9;   // מלכה
            default: return 0;
        }
    }
}