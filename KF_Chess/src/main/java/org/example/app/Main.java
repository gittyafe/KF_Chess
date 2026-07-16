package org.example.app;

import org.example.Img;
import org.example.engines.GameEngine;
import org.example.engines.GameSnapshot;
import org.example.models.Board;
import org.example.models.Piece;
import org.example.models.PieceFactory;
import org.example.models.Position;
import org.example.realtime.RealTimeArbiter;
import org.example.controllers.Controller;
import org.example.view.*;

import java.io.BufferedReader;
import java.io.FileReader;

public class Main {
    private static final int BOARD_COLS = 8;
    private static final int BOARD_ROWS = 8;

    // גודל הלוח בפיקסלים - זה המקום היחיד ששולט על גודל הלוח על המסך.
    // כדי להקטין/להגדיל את הלוח, משנים רק את הערך הזה.
    // הוקטן מ-800 ל-650 כדי לפנות מקום לכותרות/ניקוד/קואורדינטות
    // מעל ומתחת ללוח (ראו GameFrameComposer) בתוך DisplayConstants.MASTER_HEIGHT.
    private static final int BOARD_SIZE_PX = 650;
    private static final int PIECE_MARGIN_PX = 10; // ריפוד פנימי של כלי בתוך המשבצת שלו

    private static final int TICK_MS = 30; // קצב ריצה של כ-33 פריימים בשנייה

    // נתיבים למשאבים בתוך תיקיית resources
    private static final String BOARD_IMAGE = "src/main/resources/board.png";
    private static final String BOARD_CSV = "src/main/resources/board.csv";

    public static void main(String[] args) {
        // 1. אתחול מודלים ולוגיקת המשחק
        Board board = new Board(BOARD_ROWS, BOARD_COLS);
        RealTimeArbiter rta = new RealTimeArbiter();
        GameEngine gameEngine = new GameEngine(board, rta);
        Controller controller = new Controller(gameEngine);

        // טעינת הלוח מתוך קובץ ה-CSV
        loadBoardFromCSV(board, BOARD_CSV);

        // 2. אתחול ה-UI והרכיבים הויזואליים
        // BoardGeometry הוא מקור האמת היחיד לגודל הלוח והתאים - גם תמונת
        // הלוח וגם תמונות הכלים נגזרות ממנו, כך שהם תמיד מיושרים זה לזה.
        BoardGeometry geometry = new BoardGeometry(BOARD_SIZE_PX, BOARD_COLS, BOARD_ROWS, PIECE_MARGIN_PX);

        PieceImageLoader imageLoader = new PieceImageLoader(geometry);
        imageLoader.preload();

        ImgRenderer boardRenderer = new ImgRenderer(BOARD_IMAGE, geometry, imageLoader);
        GameHistoryManager historyManager = new GameHistoryManager();
        gameEngine.addMoveListener(historyManager);

        // פרטי שחקנים לתצוגה (שם + ניקוד) - top מוצג מעל הלוח (Black), bottom מתחתיו (White).
        PlayerInfo topPlayer = new PlayerInfo("Chicko Miko");    // Black
        PlayerInfo bottomPlayer = new PlayerInfo("Musti Shusti"); // White

        // ScoreManager מאזין לאירועי אכילה ומעדכן את הניקוד של השחקן שאכל,
        // לפי ערכי כלים סטנדרטיים (פרש/רץ=3, צריח=5, מלכה=9, חייל=1, מלך=0).
        ScoreManager scoreManager = new ScoreManager(bottomPlayer, topPlayer); // (white, black)
        gameEngine.addCaptureListener(scoreManager);

        GameFrameComposer composer = new GameFrameComposer(boardRenderer, historyManager, geometry, topPlayer, bottomPlayer);

        // BOARD_X/BOARD_Y/MASTER_WIDTH/MASTER_HEIGHT כבר לא שדות ציבוריים -
        // נגישים דרך static getters ב-GameFrameComposer, שהוא מקור האמת
        // היחיד לגודל הכולל, כדי שהחלון תמיד יתאים בדיוק לגודל הפריים שמורכב.
        GameWindow window = new GameWindow("Kung Fu Chess",
                GameFrameComposer.getMasterWidth(), GameFrameComposer.getMasterHeight(),
                GameFrameComposer.getBoardX(), GameFrameComposer.getBoardY());

        window.init(controller);

        // 3. לולאת המשחק הראשית (Game Loop)
        Thread gameLoop = new Thread(() -> {
            try {
                while (!gameEngine.getSnapshot().isGameOver()) {
                    long startTime = System.currentTimeMillis();

                    // עדכון לוגיקת המשחק
                    controller.wait_(TICK_MS);

                    GameSnapshot snapshot = gameEngine.getSnapshot();
                    Img frame = composer.composeFrame(snapshot);

                    // עדכון החלון בצורה בטוחה
                    window.updateFrame(frame);

                    long elapsed = System.currentTimeMillis() - startTime;
                    long sleepTime = Math.max(5, TICK_MS - elapsed);
                    Thread.sleep(sleepTime);
                }
            } catch (InterruptedException e) {
                System.out.println("Game loop interrupted.");
            }
        });

        gameLoop.start();
    }

    /**
     * טעינת הלוח מתוך קובץ CSV
     */
    private static void loadBoardFromCSV(Board board, String csvPath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(csvPath))) {
            String line;
            int rowIndex = 0;

            while ((line = reader.readLine()) != null && rowIndex < board.getHeight()) {
                String[] cells = line.split(",", -1); // -1 to preserve empty strings
                int colIndex = 0;

                for (String cell : cells) {
                    if (colIndex >= board.getWidth()) break;

                    String trimmed = cell.trim();
                    if (!trimmed.isEmpty() && trimmed.length() == 2) {
                        char color = trimmed.charAt(0);
                        char type = trimmed.charAt(1);
                        Position pos = new Position(rowIndex, colIndex);
                        Piece piece = PieceFactory.createPiece(color, type, pos);
                        board.addPiece(piece);
                    }
                    colIndex++;
                }
                rowIndex++;
            }
        } catch (Exception e) {
            System.err.println("Error loading board from CSV: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
