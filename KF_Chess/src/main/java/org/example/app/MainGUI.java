package org.example.app;

import org.example.view.Img;
import org.example.engines.GameEngine;
import org.example.engines.GameHistoryManager;
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

public class MainGUI {
    private static final int BOARD_COLS = 8;
    private static final int BOARD_ROWS = 8;
    private static final int BOARD_SIZE_PX = 650; // אפשר לשנות לכל גודל חופשי!
    private static final int PIECE_MARGIN_PX = 10;
    private static final int TICK_MS = 30;

    private static final String BOARD_IMAGE = "src/main/resources/board.png";
    private static final String BOARD_CSV = "src/main/resources/board.csv";

    public static void main(String[] args) {
        Board board = new Board(BOARD_ROWS, BOARD_COLS);
        RealTimeArbiter rta = new RealTimeArbiter();
        GameEngine gameEngine = new GameEngine(board, rta);

        loadBoardFromCSV(board, BOARD_CSV);

        BoardGeometry geometry = new BoardGeometry(BOARD_SIZE_PX, BOARD_COLS, BOARD_ROWS, PIECE_MARGIN_PX);

        Controller controller = new Controller(gameEngine);

        PieceImageLoader imageLoader = new PieceImageLoader(geometry);
        imageLoader.preload();

        ImgRenderer boardRenderer = new ImgRenderer(BOARD_IMAGE, geometry, imageLoader);
        GameHistoryManager historyManager = new GameHistoryManager();
        gameEngine.addMoveListener(historyManager);

        ScoreManager scoreManager = new ScoreManager();
        gameEngine.addCaptureListener(scoreManager);

        GameFrameComposer composer = new GameFrameComposer(boardRenderer, historyManager, geometry, scoreManager);

        GameWindow window = new GameWindow("Kung Fu Chess", 1400, 780, geometry);        window.init(controller);

        new Thread(() -> {
            while (true) {
                try {
                    GameSnapshot snapshot = gameEngine.getSnapshot();
                    if (snapshot.isGameOver()) break;

                    long startTime = System.currentTimeMillis();

                    controller.wait_(TICK_MS);

                    // שליפת snapshot טרי אחרי ההמתנה, לפריים שבאמת יצויר
                    snapshot = gameEngine.getSnapshot();

                    // שליפת מידות פעם אחת בלבד - נעילה מוחלטת לפריים הנוכחי
                    final int winWidth = window.getWidth();
                    final int winHeight = window.getHeight();

                    Img frame = composer.composeFrame(snapshot, winWidth, winHeight);

                    window.updateBoardOffsets(composer.getBoardX(), composer.getBoardY());
                    window.updateFrame(frame);

                    long elapsed = System.currentTimeMillis() - startTime;
                    Thread.sleep(Math.max(5, TICK_MS - elapsed));
                } catch (InterruptedException e) {
                    System.out.println("Game loop interrupted.");
                    break;
                } catch (Exception e) {
                    // *** תיקון הגנתי חשוב ***
                    // לפני התיקון, ה-try/catch עטף את כל ה-while מבחוץ ותפס
                    // רק InterruptedException. כל חריגה אחרת (למשל משהו
                    // שנשבר רגעית על גודל קיצוני בזמן גרירת resize) הייתה
                    // הורגת את כל ה-thread *בשקט* - המסך פשוט "קופא" בלי
                    // שום הודעת שגיאה גלויה. עכשיו כל פריים בודד עטוף
                    // בנפרד: אם משהו נכשל, מדלגים על הפריים הזה בלבד וממשיכים
                    // ללולאה הבאה, והשגיאה גם מודפסת לקונסולה - כך שאם עדיין
                    // יש קריסה, נדע בדיוק איפה ולמה (ואם זה יקרה, תשלחי לי
                    // את הפלט מהקונסולה).
                    System.err.println("Error in game loop (frame skipped): " + e);
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private static void loadBoardFromCSV(Board board, String csvPath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(csvPath))) {
            String line;
            int rowIndex = 0;
            while ((line = reader.readLine()) != null && rowIndex < board.getHeight()) {
                String[] cells = line.split(",", -1);
                int colIndex = 0;
                for (String cell : cells) {
                    if (colIndex >= board.getWidth()) break;
                    String trimmed = cell.trim();
                    if (!trimmed.isEmpty() && trimmed.length() == 2) {
                        Position pos = new Position(rowIndex, colIndex);
                        Piece piece = PieceFactory.createPiece(trimmed.charAt(0), trimmed.charAt(1), pos);
                        board.addPiece(piece);
                    }
                    colIndex++;
                }
                rowIndex++;
            }
        } catch (Exception e) {
            System.err.println("Error loading board from CSV: " + e.getMessage());
        }
    }
}
