package org.example.app;

import org.example.engines.GameHistoryManager;
import org.example.engines.GameSnapshot;
import org.example.network.ChessWebSocketClient;
import org.example.controllers.Controller;
import org.example.view.*;
import org.example.bus.GameEventBus;
import org.example.bus.GameWindowBusBridge;

import javax.swing.SwingUtilities;
import java.util.Scanner;

public class MainGUI {
    private static final int BOARD_COLS = 8;
    private static final int BOARD_ROWS = 8;
    private static final int BOARD_SIZE_PX = 650;
    private static final int PIECE_MARGIN_PX = 10;
    private static final String BOARD_IMAGE = "src/main/resources/board.png";
    private static final String SERVER_URL = "ws://localhost:8080/chess";

    // 🔒 מנגנון נעילה כדי למנוע ממתודת ה-main להסתיים בטרם עת
    private static final Object lock = new Object();

    public static void main(String[] args) {
        // 1. קלט משתמש בשילוב ולידציה בסיסית
        Scanner scanner = new Scanner(System.in);
        String username = promptForInput(scanner, "Enter your username: ", "Username can't be empty.");
        String password = promptForInput(scanner,"Enter your password: ", "Password can't be empty." );
        String roomId = promptForInput(scanner, "Enter Room ID to join/create: ", "Room ID can't be empty.");

        // 2. אתחול משאבים גרפיים קבועים (Asset Preloading)
        BoardGeometry geometry = new BoardGeometry(BOARD_SIZE_PX, BOARD_COLS, BOARD_ROWS, PIECE_MARGIN_PX);
        PieceImageLoader imageLoader = new PieceImageLoader(geometry);
        imageLoader.preload();

        ImgRenderer boardRenderer = new ImgRenderer(BOARD_IMAGE, geometry, imageLoader);
        GameHistoryManager historyManager = new GameHistoryManager();
        ScoreManager scoreManager = new ScoreManager();

        // 3. אתחול שכבת התקשורת והשליטה (בלי שום הכרה של חלונות UI)
        ChessWebSocketClient networkClient = new ChessWebSocketClient();
        Controller controller = new Controller(networkClient);

        // 4. רישום מאזינים גלובליים למחזור חיי המשחק (Lifecycle Listeners)
        setupLifecycleListeners(roomId, geometry, boardRenderer, historyManager, scoreManager, controller);

        // 5. יצירת החיבור הפיזי והצטרפות לחדר
        networkClient.connect(SERVER_URL);
        networkClient.sendJoin(username, password, roomId);

        // 🛑 החזקת ה-Thread הראשי בחיים שלא יסגר מיד אחרי השליחה לרשת
        try {
            synchronized (lock) {
                lock.wait();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Main thread interrupted: " + e.getMessage());
        }
    }

    /**
     * פונקציית עזר לניהול הקלט הטורדני מהטרמינל
     */
    private static String promptForInput(Scanner scanner, String prompt, String errorMessage) {
        System.out.print(prompt);
        String input = scanner.nextLine().trim();
        while (input.isEmpty()) {
            System.out.print(errorMessage + " " + prompt);
            input = scanner.nextLine().trim();
        }
        return input;
    }

    /**
     * ניהול כל אירועי המערכת בצורה ריאקטיבית ומנותקת
     */
    private static void setupLifecycleListeners(
            String roomId,
            BoardGeometry geometry,
            ImgRenderer boardRenderer,
            GameHistoryManager historyManager,
            ScoreManager scoreManager,
            Controller controller) {

        GameEventBus eventBus = GameEventBus.getInstance();

        // אישור כניסה לחדר (שלב ההמתנה)
        eventBus.subscribe("JOIN_ACCEPTED", data -> {
            System.out.println("✅ Joined room [" + roomId + "]. Waiting for an opponent to join...");
        });

        // דחיית כניסה לחדר
        eventBus.subscribe("JOIN_REJECTED", data -> {
            System.err.println("❌ Could not join: " + data);

            // שחרור הנעילה כדי שהתוכנית תסתיים בצורה מסודרת במקרה של שגיאה
            synchronized (lock) {
                lock.notify();
            }
            System.exit(0);
        });

        // ⚔️ המשחק התחיל: הארכיטקטורה הנקייה בהתגלמותה
        eventBus.subscribe("GAME_STARTED", data -> {
            Object[] players = (Object[]) data;
            String whiteUser = (String) players[0];
            String blackUser = (String) players[1];

            System.out.println("🚀 Game Started! White: " + whiteUser + " | Black: " + blackUser);

            // יצירת ממשק המשתמש בצורה בטוחה אך ורק על ה-UI Thread (EDT) של Swing
            SwingUtilities.invokeLater(() -> {
                // יצירת החלון וחיבורו המקומי לקונטרולר
                GameWindow window = new GameWindow("Kung Fu Chess - Room: " + roomId, 1400, 780, geometry);
                window.init(controller);

                GameFrameComposer composer = new GameFrameComposer(boardRenderer, historyManager, geometry, scoreManager, whiteUser, blackUser);
                new GameWindowBusBridge(window, composer);

                // רישום מקומי לאירועי רשת - מאחר והחלון קיים כעת בתוך ה-Scope, אין צורך ב-Setters או שדות גלובליים מסוכנים
                eventBus.subscribe("BOARD_UPDATE_RECEIVED", rawSnapshot -> {
                    GameSnapshot snapshot = (GameSnapshot) rawSnapshot;

                    // הרכבת ה-Payload מתבצעת בשכבת ה-UI בלבד שמכירה את מידות החלון העדכניות
                    GameFrameComposer.BoardUpdatePayload payload = new GameFrameComposer.BoardUpdatePayload(
                            snapshot,
                            window.getWidth(),
                            window.getHeight()
                    );

                    // עדכון הרכיבים ב-Thread המתאים
                    SwingUtilities.invokeLater(() -> {
                        controller.updateSnapshot(payload.snapshot());
                        eventBus.publish("BOARD_UPDATE", payload);
                    });
                });

                // 🔓 ברגע שהחלון נוצר והגרפיקה של Swing באוויר, אפשר לשחרר בבטחה את ה-main thread
                synchronized (lock) {
                    lock.notify();
                }
            });
        });
    }
}