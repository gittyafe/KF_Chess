package org.example.app;

import org.example.engines.GameEngine;
import org.example.engines.GameHistoryManager;
import org.example.engines.GameSnapshot;
import org.example.models.Board;
import org.example.models.Piece;
import org.example.models.PieceFactory;
import org.example.models.Position;
import org.example.network.ChessWebSocketClient;
import org.example.realtime.RealTimeArbiter;
import org.example.controllers.Controller;
import org.example.view.*;
import org.example.bus.GameEventBus;
import org.example.bus.CaptureBusAdapter;
import org.example.bus.MoveBusAdapter;
import org.example.bus.GameWindowBusBridge;

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
        ScoreManager scoreManager = new ScoreManager();

        gameEngine.addMoveListener(new MoveBusAdapter());
        gameEngine.addCaptureListener(new CaptureBusAdapter());

        GameFrameComposer composer = new GameFrameComposer(boardRenderer, historyManager, geometry, scoreManager);

        GameWindow window = new GameWindow("Kung Fu Chess", 1400, 780, geometry);        window.init(controller);

        // Replaces the old direct window.updateFrame(...) call at the end of
        // every tick below - the bridge listens for FRAME_READY and drives
        // GameWindow's existing public methods itself.
        new GameWindowBusBridge(window, composer);

        ChessWebSocketClient client = new ChessWebSocketClient(window);
        client.connect("ws://localhost:8080/chess");

        new Thread(() -> {
            while (true) {
                try {
                    GameSnapshot snapshot = gameEngine.getSnapshot();
                    if (snapshot.isGameOver()) break;

                    long startTime = System.currentTimeMillis();

                    controller.wait_(TICK_MS);

                    snapshot = gameEngine.getSnapshot();

                    final int winWidth = window.getWidth();
                    final int winHeight = window.getHeight();

                    // Always publish local updates
                    GameEventBus.getInstance().publish("BOARD_UPDATE",
                            new GameFrameComposer.BoardUpdatePayload(snapshot, winWidth, winHeight));

                    long elapsed = System.currentTimeMillis() - startTime;
                    Thread.sleep(Math.max(5, TICK_MS - elapsed));
                } catch (InterruptedException e) {
                    System.out.println("Game loop interrupted.");
                    break;
                } catch (Exception e) {
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
