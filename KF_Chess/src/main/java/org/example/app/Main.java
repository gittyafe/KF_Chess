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

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final int CELL_SIZE = 100;
    private static final int TICK_MS = 30; // קצב ריצה של כ-33 פריימים בשנייה

    // נתיבים למשאבים בתוך תיקיית resources
    private static final String BOARD_IMAGE = "src/main/resources/board.png";
    private static final String BOARD_CSV = "src/main/resources/board.csv";

    public static void main(String[] args) {
        // 1. אתחול מודלים ולוגיקת המשחק
        Board board = new Board(8, 8);
        RealTimeArbiter rta = new RealTimeArbiter();
        GameEngine gameEngine = new GameEngine(board, rta);
        Controller controller = new Controller(gameEngine);

        // טעינת הלוח מתוך קובץ ה-CSV
        loadBoardFromCSV(board, BOARD_CSV);

        // 2. אתחול ה-UI והרכיבים הויזואליים
        PieceImageLoader imageLoader = new PieceImageLoader(CELL_SIZE);
        imageLoader.preload();

        ImgRenderer boardRenderer = new ImgRenderer(BOARD_IMAGE, CELL_SIZE, imageLoader);
        GameFrameComposer composer = new GameFrameComposer(boardRenderer);
        GameWindow window = new GameWindow("Kung Fu Chess", 1100, 800);

        window.init(controller);

        // 3. לולאת המשחק הראשית (Game Loop)
        Thread gameLoop = new Thread(() -> {
            try {
                // תיקון: שימוש ב-getSnapshot(gameEngine) המקומי במקום gameEngine.snapshot()
                while (!getSnapshot(board, gameEngine, rta).isGameOver()) {
                    long startTime = System.currentTimeMillis();

                    // עדכון לוגיקת המשחק
                    controller.wait_(TICK_MS);

                    // תיקון: שימוש ב-getSnapshot(gameEngine) המקומי
                    GameSnapshot snapshot = getSnapshot(board, gameEngine, rta);
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
                        // בתוך הלולאה ב-loadBoardFromCSV
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



    /**
     * פונקציית עזר המעתיקה את מצב הלוח הנוכחי למבנה נתונים קבוע לצורך ציור בטוח
     */
    private static GameSnapshot getSnapshot(Board board, GameEngine engine, RealTimeArbiter rta) {
        List<PieceSnapshot> pieces = new ArrayList<>();

        for (int r = 0; r < board.getHeight(); r++) {
            for (int c = 0; c < board.getWidth(); c++) {
                Position pos = new Position(r, c);
                Piece piece = engine.getPieceAt(pos);
                if (piece != null) {
                    Position targetPos = pos; // כברירת מחדל, היעד הוא המיקום הנוכחי
                    if (rta.getMovingPiece(piece.getId()) != null) {
                        // בדיקה: אם יש כרגע כלי פעיל ב-MovingPiece ב-RTA, והוא הכלי הנוכחי שלנו// נשלוף את משבצת היעד האמיתית מתוך ה-MovingPiece!
                        targetPos = rta.getMovingPiece(piece.getId()).getDestination();
                    }
                    pieces.add(new PieceSnapshot(
                            piece.getId(),
                            piece.getType(),
                            piece.getColor(),
                            pos,
                            targetPos,
                            piece.getState()
                    ));
                }
            }
        }

        return new GameSnapshot(pieces, engine.isGameOver());
    }

}