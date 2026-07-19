package org.example.view;

import java.awt.Color;
import java.util.List;
import org.example.Img;
import org.example.engines.GameHistoryManager;
import org.example.engines.GameSnapshot;
import org.example.engines.MoveEntry;

public class GameFrameComposer {
    private final ImgRenderer boardRenderer;
    private final GameHistoryManager historyManager;
    private final BoardGeometry geometry;
    private final ScoreManager scoreManager;

    private int lastBoardX = 0;
    private int lastBoardY = 0;

    public GameFrameComposer(ImgRenderer boardRenderer, GameHistoryManager historyManager,
                             BoardGeometry geometry, ScoreManager scoreManager) {
        this.boardRenderer = boardRenderer;
        this.historyManager = historyManager;
        this.geometry = geometry;
        this.scoreManager = scoreManager;
    }

    public int getBoardX() { return lastBoardX; }
    public int getBoardY() { return lastBoardY; }

    public Img composeFrame(GameSnapshot snapshot, int currentWindowWidth, int currentWindowHeight) {
        // מניעת קריסה על גדלים לא חוקיים בזמן כיווץ מהיר
        int width = Math.max(400, currentWindowWidth);
        int height = Math.max(300, currentWindowHeight);

        // לכידת גודל קבוע ונעול לפריים הנוכחי בלבד!
        int calculatedBoardSize = Math.min((int)(width * 0.55), height - 160);
        calculatedBoardSize = Math.max(200, calculatedBoardSize);

        // עדכון הגיאומטריה פעם אחת בלבד
        geometry.updateSize(calculatedBoardSize);

        this.lastBoardY = (height - calculatedBoardSize) / 2;
        this.lastBoardX = (width - calculatedBoardSize) / 2;

        int panelWidth = Math.min(220, (int)(width * 0.18));

        Img masterFrame = new Img().createEmpty(width, height, true);

        // רקע
        masterFrame.drawRect(0, 0, width, height, new Color(225, 227, 230));
        drawOutline(masterFrame, 0, 0, width, height, new Color(70, 110, 150), 4);

        // ציור הלוח (העברת המידות הנעולות פנימה)
        Img boardImg = boardRenderer.drawGame(snapshot);
        boardImg.drawOn(masterFrame, lastBoardX, lastBoardY);

        // קואורדינטות וטקסטים מבוססי מידות נעולות
        drawColumnLabels(masterFrame, lastBoardX, lastBoardY - 20);
        drawColumnLabels(masterFrame, lastBoardX, lastBoardY + calculatedBoardSize + 25);
        drawRowLabels(masterFrame, lastBoardX, lastBoardY, calculatedBoardSize);

        int leftPanelX = (lastBoardX - panelWidth) / 2;
        drawPlayerPanel(masterFrame, "BLACK", scoreManager.getBlackScore(), historyManager.blackMoves, leftPanelX, lastBoardY, calculatedBoardSize, panelWidth);

        int rightPanelX = lastBoardX + calculatedBoardSize + leftPanelX;
        drawPlayerPanel(masterFrame, "WHITE", scoreManager.getWhiteScore(), historyManager.whiteMoves, rightPanelX, lastBoardY, calculatedBoardSize, panelWidth);

        return masterFrame;
    }

    // שאר המתודות הפרטיות (drawPlayerPanel, drawColumnLabels וכו') נשארות כרגיל
    private void drawPlayerPanel(Img frame, String name, int score, List<MoveEntry> moves, int x, int boardY, int boardSize, int panelWidth) {
        int centerX = x + (panelWidth / 2);
        drawCenteredText(frame, "Player: " + name, centerX, boardY - 30, 1.2f, Color.BLACK);
        int scoreBoxY = boardY;
        frame.drawRect(x, scoreBoxY, panelWidth, 50, Color.WHITE);
        drawOutline(frame, x, scoreBoxY, panelWidth, 50, new Color(160, 170, 185), 1);
        drawCenteredText(frame, "SCORE: " + score, centerX, scoreBoxY + 32, 1.2f, new Color(50, 70, 95));
        int tableY = boardY + 60;
        int tableHeight = boardSize - 60;
        frame.drawRect(x, tableY, panelWidth, tableHeight, Color.WHITE);
        drawOutline(frame, x, tableY, panelWidth, tableHeight, new Color(160, 170, 185), 1);
        frame.drawRect(x, tableY, panelWidth, 30, new Color(210, 215, 225));
        frame.putText("Time", x + 15, tableY + 20, 0.7f, Color.BLACK, 2);
        frame.putText("Move", x + (panelWidth / 2) + 10, tableY + 20, 0.7f, Color.BLACK, 2);
        int rowY = tableY + 30;
        int rowHeight = 28;
        int maxRows = (tableHeight - 30) / rowHeight;
        for (int i = 0; i < Math.min(moves.size(), maxRows); i++) {
            MoveEntry move = moves.get(i);
            if (i % 2 == 1) frame.drawRect(x, rowY, panelWidth, rowHeight, new Color(245, 247, 250));
            frame.putText(move.timeString(), x + 15, rowY + 18, 1.2f, Color.DARK_GRAY, 0);
            frame.putText(move.moveNotation(), x + (panelWidth / 2) + 10, rowY + 18, 1.2f, Color.BLACK, 0);
            rowY += rowHeight;
        }
    }

    private void drawColumnLabels(Img frame, int boardX, int y) {
        int cellSize = geometry.getCellSize();
        char[] letters = {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'};
        for (int col = 0; col < letters.length; col++) {
            int cellCenterX = boardX + col * cellSize + cellSize / 2;
            frame.putText(String.valueOf(letters[col]), cellCenterX - 5, y, 1.1f, Color.BLACK, 1);
        }
    }

    private void drawRowLabels(Img frame, int boardX, int boardY, int boardSize) {
        int cellSize = geometry.getCellSize();
        int leftX = boardX - 25;
        int rightX = boardX + boardSize + 12;
        for (int row = 0; row < geometry.getRows(); row++) {
            int cellCenterY = boardY + row * cellSize + cellSize / 2 + 6;
            String text = String.valueOf(row + 1);
            frame.putText(text, leftX, cellCenterY, 1.1f, Color.BLACK, 1);
            frame.putText(text, rightX, cellCenterY, 1.1f, Color.BLACK, 1);
        }
    }

    private void drawCenteredText(Img frame, String text, int centerX, int y, float fontSize, Color color) {
        int textWidth = text.length() * (int) (fontSize * 7.5);
        frame.putText(text, centerX - textWidth / 2, y, fontSize, color, 2);
    }

    private void drawOutline(Img frame, int x, int y, int w, int h, Color color, int thickness) {
        frame.drawRect(x, y, w, thickness, color);
        frame.drawRect(x, y + h - thickness, w, thickness, color);
        frame.drawRect(x, y, thickness, h, color);
        frame.drawRect(x + w - thickness, y, thickness, h, color);
    }
}