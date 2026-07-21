package org.example.app;

import org.example.engines.GameHistoryManager;
import org.example.engines.GameSnapshot;
import org.example.models.Role;
import org.example.network.ChessWebSocketClient;
import org.example.controllers.NetworkController;
import org.example.view.*;
import org.example.bus.GameEventBus;
import org.example.bus.GameWindowBusBridge;

import javax.swing.SwingUtilities;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainGUI {
    private static final int BOARD_COLS = 8;
    private static final int BOARD_ROWS = 8;
    private static final int BOARD_SIZE_PX = 650;
    private static final int PIECE_MARGIN_PX = 10;
    private static final String BOARD_IMAGE = "src/main/resources/board.png";
    private static final String SERVER_URL = "ws://localhost:8080/chess";

    private static final Object lock = new Object();
    private static volatile GameWindow activeWindow = null;

    // 🟢 1. הגדרת המשתנה למניעת הצפת תור ה-EDT
    private static final AtomicBoolean isRendering = new AtomicBoolean(false);

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        String username = promptForInput(scanner, "Enter your username: ", "Username can't be empty.");
        String password = promptForInput(scanner, "Enter your password: ", "Password can't be empty.");
        String roomId = promptForInput(scanner, "Enter Room ID to join/create: ", "Room ID can't be empty.");

        BoardGeometry geometry = new BoardGeometry(BOARD_SIZE_PX, BOARD_COLS, BOARD_ROWS, PIECE_MARGIN_PX);
        PieceImageLoader imageLoader = new PieceImageLoader(geometry);
        imageLoader.preload();

        ImgRenderer boardRenderer = new ImgRenderer(BOARD_IMAGE, geometry, imageLoader);
        GameHistoryManager historyManager = new GameHistoryManager();
        ScoreManager scoreManager = new ScoreManager();

        ChessWebSocketClient networkClient = new ChessWebSocketClient();
        NetworkController controller = new NetworkController(networkClient, Role.UNKNOWN);

        setupLifecycleListeners(username, roomId, geometry, boardRenderer, historyManager, scoreManager, controller);

        networkClient.connect(SERVER_URL);
        networkClient.sendJoin(username, password, roomId);

        try {
            synchronized (lock) {
                lock.wait();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String promptForInput(Scanner scanner, String prompt, String errorMessage) {
        System.out.print(prompt);
        String input = scanner.nextLine().trim();
        while (input.isEmpty()) {
            System.out.print(errorMessage + " " + prompt);
            input = scanner.nextLine().trim();
        }
        return input;
    }

    private static void setupLifecycleListeners(
            String username,
            String roomId,
            BoardGeometry geometry,
            ImgRenderer boardRenderer,
            GameHistoryManager historyManager,
            ScoreManager scoreManager,
            NetworkController controller) {

        GameEventBus eventBus = GameEventBus.getInstance();

        eventBus.subscribe("JOIN_ACCEPTED", data -> {
            System.out.println("✅ Joined room [" + roomId + "]. Waiting for game state...");
        });

        eventBus.subscribe("JOIN_REJECTED", data -> {
            System.err.println("❌ Could not join: " + data);
            synchronized (lock) {
                lock.notifyAll();
            }
            System.exit(0);
        });

        // 🟢 2. פתרון 2 מיושם כאן: הוספת בקרת הקצב לאירועי הלוח
        eventBus.subscribe("BOARD_UPDATE_RECEIVED", rawSnapshot -> {
            GameSnapshot snapshot = (GameSnapshot) rawSnapshot;
            controller.updateSnapshot(snapshot);

            GameWindow window = activeWindow;
            if (window != null) {
                // בדיקה אם ה-EDT פנוי. אם הוא עדיין מרנדר את הפריים הקודם - נדלג על השליחה
                if (isRendering.compareAndSet(false, true)) {
                    SwingUtilities.invokeLater(() -> {
                        try {
                            GameFrameComposer.BoardUpdatePayload payload = new GameFrameComposer.BoardUpdatePayload(
                                    snapshot,
                                    window.getWidth(),
                                    window.getHeight()
                            );
                            eventBus.publish("BOARD_UPDATE", payload);
                        } finally {
                            isRendering.set(false); // שחרור הדגל
                        }
                    });
                }
            }
        });

        eventBus.subscribe("GAME_STARTED", data -> {
            Object[] players = (Object[]) data;
            String whiteUser = (String) players[0];
            String blackUser = (String) players[1];

            System.out.println("🚀 Game Active! White: " + whiteUser + " | Black: " + blackUser);

            Role userRole;
            if (username.equalsIgnoreCase(whiteUser)) {
                userRole = Role.WHITE;
            } else if (username.equalsIgnoreCase(blackUser)) {
                userRole = Role.BLACK;
            } else {
                userRole = Role.SPECTATOR;
            }

            controller.setRole(userRole);

            SwingUtilities.invokeLater(() -> {
                if (activeWindow != null) return;

                GameWindow window = new GameWindow("Kung Fu Chess - Room: " + roomId, 1400, 780, geometry);
                activeWindow = window;

                window.init(controller);
                window.updateRole(userRole);

                GameFrameComposer composer = new GameFrameComposer(boardRenderer, historyManager, geometry, scoreManager, whiteUser, blackUser);
                new GameWindowBusBridge(window, composer);

                if (controller.getLatestSnapshot() != null) {
                    GameFrameComposer.BoardUpdatePayload payload = new GameFrameComposer.BoardUpdatePayload(
                            controller.getLatestSnapshot(),
                            window.getWidth(),
                            window.getHeight()
                    );
                    eventBus.publish("BOARD_UPDATE", payload);
                }
            });
        });
    }
}