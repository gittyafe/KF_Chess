
import models.MoveRequest;
import models.Position;


/**
 * Manages piece selection and move initiation
 */

public class Controller {

    private boolean isSelected = false;
    private Position selectedPosition = new Position(-1, -1);
    private final GameEngine gameEngine;

    public Controller(GameEngine gameEngine) {
        this.gameEngine = gameEngine;
    }

    public void click(int x, int y) {
        int targetRow = BoardMapper.pixelToCell(y);
        int targetCol = BoardMapper.pixelToCell(x);
        Position targetPosition = new Position(targetRow, targetCol);
        // אם זו לחיצה ראשונה (בחירת כלי)
        if (!isSelected) {
            // נשאל את המנוע אם יש שם כלי של השחקן הנוכחי שאפשר לבחור
            if (gameEngine.getPieceAt(targetPosition) != null) {
                isSelected = true;
                selectedPosition = targetPosition;
            }
            return;
        }

        // אם זו לחיצה שנייה (ניסיון תנועה)
        MoveRequest moveResult = gameEngine.requestMove(selectedPosition, targetPosition);
        switch (moveResult.getReason()) {
            case SUCCESS:
                // המנוע כבר עדכן את ה-STATE של הכלי בפנים והפעיל את האנימציה!
                clearSelection();
                break;

            case SAME_COLOR_OCCUPIED:
                // החלפת בחירה לכלי השני מאותו הצבע
                selectedPosition = targetPosition;
                break;

            default:
                // מהלך שגוי או תנועה שעדיין א"א לעשות או מחוץ ללוח - מבטלים בחירה
                clearSelection();
                break;
        }
    }

    public void clearSelection() {
        isSelected = false;
        selectedPosition = new Position(-1, -1); // עדיף ליצור חדש מאשר לשנות מוטציה פנימית בטעות
    }

    public void jump(int x, int y){
        int row = BoardMapper.pixelToCell(y);
        int col = BoardMapper.pixelToCell(x);
        Position jumpPosition = new Position(row, col);

        gameEngine.jumpRequest(jumpPosition);
    }
}

