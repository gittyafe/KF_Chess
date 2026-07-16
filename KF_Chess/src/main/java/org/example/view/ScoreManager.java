package org.example.view;

/**
 * מאזין שמעדכן את הניקוד המוצג (PlayerInfo) בכל פעם שכלי נאכל,
 * לפי ערכי הכלים ב-PieceValues. הניקוד מתווסף לשחקן שביצע את האכילה.
 */
public class ScoreManager implements CaptureListener {
    private final PlayerInfo whitePlayer;
    private final PlayerInfo blackPlayer;


    public ScoreManager(PlayerInfo whitePlayer, PlayerInfo blackPlayer) {
        this.whitePlayer = whitePlayer;
        this.blackPlayer = blackPlayer;
    }

    @Override
    public void onPieceCaptured(char capturedType, char capturingColor) {
        int points = getValue(capturedType);
        if (points <= 0) {
            return; // אכילת מלך לא מוסיפה ניקוד - המשחק ממילא מסתיים
        }

        if (capturingColor == 'w') {
            whitePlayer.score += points;
        } else if (capturingColor == 'b') {
            blackPlayer.score += points;
        }
    }

    public int getValue(char pieceType) {
        pieceType = Character.toLowerCase(pieceType);
        switch (pieceType) {
            case 'p': return 1;   // רגלי
            case 'n': return 3;   // פרש
            case 'b': return 3;   // רץ
            case 'r': return 5;   // צריח
            case 'q': return 9;   // מלכה
            case 'k': return 0;   // מלך (אין ערך)
            default: return 0;    // סוג לא מוכר
        }
    }
}
