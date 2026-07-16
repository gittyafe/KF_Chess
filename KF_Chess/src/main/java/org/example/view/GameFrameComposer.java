package org.example.view;

import java.awt.Color;
import java.util.List;

import org.example.Img;
import org.example.models.State;
import org.example.engines.GameSnapshot;
public class GameFrameComposer {
    private final ImgRenderer boardRenderer;
    private final GameHistoryManager historyManager; // מחזיק את הנתונים
    public int BOARD_X = 250;
    public int BOARD_Y = 50;


    public GameFrameComposer(ImgRenderer boardRenderer, GameHistoryManager historyManager) {
        this.boardRenderer = boardRenderer;
        this.historyManager = historyManager;
    }

    public Img composeFrame(GameSnapshot snapshot) {
        // 1. ה-Renderer נשאר מקורי - לא נגענו בו!
        Img boardImg = boardRenderer.drawGame(snapshot);

        // 2. הגדרת המיקום שבו נרצה את הלוח במאסטר


        // 3. יצירת המאסטר בגודל שיכיל הכל בוודאות (הלוח + שוליים)
        // אם הלוח בגודל 828, המאסטר צריך להיות לפחות 828 + 50 + 100 = 978
        int masterW = 1400;
        int masterH = 950;
        Img masterFrame = new Img().createEmpty(masterW, masterH, true);

        // 4. ציור בטוח: אנחנו לא מחשבים קואורדינטות לכלים,
        // אנחנו מציירים את ה-Img השלם שקיבלנו מה-Renderer
        boardImg.drawOn(masterFrame, BOARD_X, BOARD_Y);

        // 5. ציור הטבלאות סביב הלוח
        drawHistoryTable(masterFrame, historyManager.blackMoves, 20, 50, "Black", new Color(240, 240, 240));
        drawHistoryTable(masterFrame, historyManager.whiteMoves, 1150, 50, "White", new Color(240, 240, 240));

        return masterFrame;
    }
    private void drawHistoryTable(Img frame, List<MoveEntry> moves, int x, int y, String title, Color bgColor) {
        // רקע הטבלה
            int tableWidth = 200;
            if (x < 0 || x + tableWidth > frame.get().getWidth()) return;

            frame.drawRect(x, y, tableWidth, 500, bgColor);

        // כותרת הטבלה
        frame.putText(title, x + 70, y + 25, 2.0f, Color.BLACK, 2);
        frame.drawLine(x, y + 40, x + 200, y + 40, Color.GRAY); // קו מתחת לכותרת

        int rowY = y + 70;
        for (MoveEntry move : moves) {
            frame.putText(move.getTimeString(), x + 10, rowY, 1.7f, Color.DARK_GRAY, 0);
            frame.putText(move.getMoveNotation(), x + 120, rowY, 1.7f, Color.BLACK, 0);
            rowY += 30; // מרווח שורות
        }
    }
}