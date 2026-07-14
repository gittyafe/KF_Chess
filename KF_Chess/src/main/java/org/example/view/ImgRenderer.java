package org.example.view;


import java.awt.Color;

import org.example.Img;
import org.example.engines.GameSnapshot;
import org.example.engines.PieceSnapshot;
import org.example.models.State;

public class ImgRenderer {
    private final String boardPath;
    private final int cellSize;
    private final PieceImageLoader imageLoader;

    public ImgRenderer(String boardPath, int cellSize, PieceImageLoader imageLoader) {
        this.boardPath = boardPath;
        this.cellSize = cellSize;
        this.imageLoader = imageLoader;
    }

    /**
     * Composes a complete Img frame based on the GameSnapshot.
     */
    public Img drawGame(GameSnapshot snapshot) {
        // 1. נטען מחדש קנבס נקי של רקע הלוח
        Img frameCanvas = new Img().read(boardPath);

        // 2. נצייר את כל הכלים הפעילים
        // 2. נצייר את כל הכלים הפעילים
        for (PieceSnapshot piece : snapshot.pieces()) {
            // שליפה דינמית מה-Loader לפי הצבע, הסוג והמצב הנוכחי של הכלי באותו פריים!
            Img pieceImg = imageLoader.getPieceImage(piece.color(), piece.type(), piece.state());

            if (pieceImg != null) {
                int x = piece.position().getColumn() * cellSize + 10;
                int y = piece.position().getRow() * cellSize + 10;

                // ציור הכלי על גבי הקנבס
                pieceImg.drawOn(frameCanvas, x, y);

                // אם הכלי נמצא ב-Cooldown או מצב תנועה, נציג חיווי
                if (piece.state() != State.IDLE) {
                    frameCanvas.putText(piece.state().toString(), x + 5, y + cellSize - 20, 0.8f, Color.RED, 0);
                }
            }
        }

        // 3. אם יש Game Over, נוסיף כיתוב מתאים באמצע
        if (snapshot.isGameOver()) {
            frameCanvas.putText("GAME OVER", 200, 420, 4.0f, Color.YELLOW, 0);
        }

        return frameCanvas;
    }
}