

import java.util.ArrayList;
import java.util.List;
import models.MovingPiece;
import models.Piece;
import models.Position;

public class RealTimeEngine {
    
    // הפרדה מלאה בזיכרון בין סוגי התנועות
    private MovingPiece activeMotion = null;
    private MovingPiece activeJump = null;
    
    private static final int MS_PER_CELL = 1000;

    // הגדרת כלי בתנועה רגילה
    public void setActiveMotion(int distanceCells, Piece piece, Position destination) {
        if (hasActiveMotion()) {
            throw new IllegalStateException("A piece is already in motion!");
        }
        this.activeMotion = new MovingPiece(piece, destination, distanceCells * MS_PER_CELL);
    }

    // הגדרת כלי בקפיצה מבוססת זמן
    public void setActiveJump(Piece piece) {
        if (hasActiveJump()) {
            throw new IllegalStateException("A piece is already jumping!");
        }
        this.activeJump = new MovingPiece(piece, piece.getPosition(), MS_PER_CELL);
    }

    public boolean hasActiveMotion() {
        return this.activeMotion != null;
    }

    public boolean hasActiveJump() {
        return this.activeJump != null;
    }

    /**
     * מעדכן את הזמן עבור שני סוגי התנועות ומחזיר את אלו שסיימו את הזמן שלהם
     */
    public List<MovingPiece> updateTime(long deltaTime) {
        List<MovingPiece> finishedThisTick = new ArrayList<>();

        // 1. עדכון שעון התנועה הרגילה
        if (activeMotion != null) {
            activeMotion.decrementTimeLeft(deltaTime);
            if (activeMotion.isTimeUp()) {
                finishedThisTick.add(activeMotion);
                activeMotion = null; // פינוי המקום לתנועה הבאה
            }
        }

        // 2. עדכון שעון הקפיצה
        if (activeJump != null) {
            activeJump.decrementTimeLeft(deltaTime);
            if (activeJump.isTimeUp()) {
                finishedThisTick.add(activeJump);
                activeJump = null; // פינוי המקום לקפיצה הבאה
            }
        }

        return finishedThisTick;
    }
}