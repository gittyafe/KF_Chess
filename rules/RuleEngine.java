package rules;

import models.Board;
import models.Piece;
import models.Position;

public class RuleEngine {

    /**
     * פונקציית השרשרת הראשית (Validation Pipeline)
     */
    public static boolean isValidMove(Piece piece, Position src, Position dest, Board board) {
        Piece targetPiece = board.queryPieceAt(dest);
        boolean isCapture = (targetPiece != null);

        // שלב 1: בדיקת וקטור תנועה גיאומטרי יבש
        if (!MoveGeometry.isDirectionValid(piece.getType(), src, dest, piece.getColor(), isCapture, board.getHeight())) {
            return false;
        }

        // שלב 2: בדיקת חסימת מסלול לכלים ארוכי טווח (Rook, Bishop, Queen)
        if (requiresPathClearCheck(piece.getType()) && !isPathClear(src, dest, board)) {
            return false;
        }

        // שלב 3: בדיקת חסימות ייחודיות בהתאם להקשר הלוח (כמו צעד כפול של רגלי)
        if (!checkContextualObstacles(piece, src, dest, board)) {
            return false;
        }

        return true;
    }

    private static boolean requiresPathClearCheck(char type) {
        char t = Character.toUpperCase(type);
        return t == 'R' || t == 'B' || t == 'Q';
    }

    /**
     * בדיקת חסימת מסלול גנרית ואלגוריתמית
     */
    private static boolean isPathClear(Position src, Position dest, Board board) {
        int stepRow = Integer.compare(dest.getRow(), src.getRow());
        int stepCol = Integer.compare(dest.getColumn(), src.getColumn());

        int currRow = src.getRow() + stepRow;
        int currCol = src.getColumn() + stepCol;

        while (currRow != dest.getRow() || currCol != dest.getColumn()) {
            if (board.queryPieceAt(currRow, currCol) != null) {
                return false;
            }
            currRow += stepRow;
            currCol += stepCol;
        }
        return true;
    }

    /**
     * ריכוז בדיקות חסימה תלויות-לוח מיוחדות
     */
    private static boolean checkContextualObstacles(Piece piece, Position src, Position dest, Board board) {
        char type = Character.toUpperCase(piece.getType());
        
        // החרגה ייחודית לרגלי: מניעת קפיצה מעל כלי בצעד כפול קדימה
        if (type == 'P' && Math.abs(dest.getRow() - src.getRow()) == 2) {
            int stepRow = (piece.getColor() == 'w') ? -1 : 1;
            if (board.queryPieceAt(src.getRow() + stepRow, src.getColumn()) != null) {
                return false;
            }
        }
        
        // כאן בעתיד ייכנסו בדיקות כמו: האם המלך עובר דרך משבצת מאוימת בזמן הצרחה
        return true;
    }
}