package org.example.app;


import org.example.Img;
import org.example.engines.GameEngine;
import org.example.engines.GameSnapshot;
import org.example.engines.PieceSnapshot;
import org.example.models.Board;
import org.example.models.Piece;
import org.example.models.PieceFactory;
import org.example.models.Position;
import org.example.realtime.RealTimeArbiter;
import org.example.controllers.Controller;
import org.example.view.*;

import java.util.ArrayList;
import java.util.List;
public class Main {
    private static final int CELL_SIZE = 100;
    private static final int TICK_MS = 30; // קצב ריצה של כ-33 פריימים בשנייה

    // נתיבים למשאבים בתוך תיקיית resources
    private static final String BOARD_IMAGE = "src/main/resources/board.png";
    private static final String BACKGROUND_IMAGE = "src/main/resources/main_background.png";

    public static void main(String[] args) {
        // 1. אתחול מודלים ולוגיקת המשחק
        Board board = new Board(8, 8);
        RealTimeArbiter rta = new RealTimeArbiter();
        GameEngine gameEngine = new GameEngine(board, rta);
        Controller controller = new Controller(gameEngine);

        // אכלוס ראשוני של הלוח בעזרת ה-PieceFactory שלך
        board.addPiece(PieceFactory.createPiece('w', 'R', new Position(7, 0))); // צריח לבן
        board.addPiece(PieceFactory.createPiece('w', 'K', new Position(7, 4))); // מלך לבן
        board.addPiece(PieceFactory.createPiece('b', 'K', new Position(0, 4))); // מלך שחור

        // 2. אתחול הגרפיקה וה-View בסדר הנכון
        // טוענים קודם כל את הכלים לזיכרון כדי למנוע לאגים במשחק
        PieceImageLoader imageLoader = new PieceImageLoader(CELL_SIZE);
        imageLoader.preload();

        // יצירת ה-Renderer של הלוח (800x800) המשתמש ב-imageLoader
        ImgRenderer boardRenderer = new ImgRenderer(BOARD_IMAGE, CELL_SIZE, imageLoader);

        // המאחד (Composer) שיחבר את הלוח הדינמי עם רקע הסיידבר המובנה
        GameFrameComposer composer = new GameFrameComposer(boardRenderer);

        // יצירת חלון המשחק הראשי ברוחב המורחב (1100 פיקסלים)
        GameWindow window = new GameWindow("Kung Fu Chess - Real Time", 1100, 800);
        window.init(controller);

        // 3. לולאת המשחק הראשית בזמן אמת (Game Loop)
        try {
            while (!gameEngine.isGameOver()) {
                // א. עדכון מנוע המשחק והזמן (למשל Cooldowns ופיזיקת תנועה)
                gameEngine.wait_(TICK_MS);

                // ב. יצירת GameSnapshot לקריאה בלבד (מניעת Race Conditions)
                GameSnapshot snapshot = createSnapshot(board, gameEngine);

                // ג. יצירת הפריים המלא (לוח משמאל + סיידבר טקסטואלי מימין)
                org.example.Img fullFrame = composer.composeFrame(snapshot);
                window.updateFrame(fullFrame);

                // ד. השהיית הלולאה לקצב קבוע
                Thread.sleep(TICK_MS);
            }
        } catch (InterruptedException e) {
            System.err.println("Game loop interrupted.");
        }
    }

    /**
     * פונקציית עזר המעתיקה את מצב הלוח הנוכחי למבנה נתונים קבוע לצורך ציור בטוח
     */
    private static GameSnapshot createSnapshot(Board board, GameEngine engine) {
        List<PieceSnapshot> pieces = new ArrayList<>();

        for (int r = 0; r < board.getHeight(); r++) {
            for (int c = 0; c < board.getWidth(); c++) {
                Position pos = new Position(r, c);
                Piece piece = engine.getPieceAt(pos);
                if (piece != null) {
                    pieces.add(new PieceSnapshot(
                            piece.getType(),
                            piece.getColor(),
                            pos,
                            piece.getState()
                    ));
                }
            }
        }
        return new GameSnapshot(pieces, engine.isGameOver());
    }
}