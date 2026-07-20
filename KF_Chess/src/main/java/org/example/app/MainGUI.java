package org.example.app;

import org.example.engines.GameHistoryManager;
import org.example.network.ChessWebSocketClient;
import org.example.controllers.Controller;
import org.example.view.*;
import org.example.bus.GameEventBus;
import org.example.bus.GameWindowBusBridge;

public class MainGUI {
    private static final int BOARD_COLS = 8;
    private static final int BOARD_ROWS = 8;
    private static final int BOARD_SIZE_PX = 650; // אפשר לשנות לכל גודל חופשי!
    private static final int PIECE_MARGIN_PX = 10;

    private static final String BOARD_IMAGE = "src/main/resources/board.png";

    public static void main(String[] args) {
        // 🌐 Fully networked client: there is no local GameEngine/Board here
        // anymore. The server is the sole source of truth for game state -
        // everything rendered comes from BOARD_UPDATE snapshots it sends,
        // and every click is turned into a move command sent back to it.
        // (Board/GameEngine/RealTimeArbiter/loadBoardFromCSV, and the local
        // per-tick loop that used to run them, were removed - they only ever
        // drove a second, disconnected copy of the game that nothing kept in
        // sync with what was actually on screen.)

        BoardGeometry geometry = new BoardGeometry(BOARD_SIZE_PX, BOARD_COLS, BOARD_ROWS, PIECE_MARGIN_PX);

        PieceImageLoader imageLoader = new PieceImageLoader(geometry);
        imageLoader.preload();

        ImgRenderer boardRenderer = new ImgRenderer(BOARD_IMAGE, geometry, imageLoader);
        GameHistoryManager historyManager = new GameHistoryManager();
        ScoreManager scoreManager = new ScoreManager();

        GameFrameComposer composer = new GameFrameComposer(boardRenderer, historyManager, geometry, scoreManager);

        GameWindow window = new GameWindow("Kung Fu Chess", 1400, 780, geometry);

        ChessWebSocketClient client = new ChessWebSocketClient(window);

        Controller controller = new Controller(client);
        window.init(controller);

        // Replaces the old direct window.updateFrame(...) call - the bridge
        // listens for FRAME_READY and drives GameWindow's existing public
        // methods itself.
        new GameWindowBusBridge(window, composer);

        // Keep Controller's click hit-testing in sync with whatever the
        // server most recently said is on the board, so a click always
        // matches what's actually rendered.
        GameEventBus.getInstance().subscribe("BOARD_UPDATE", data -> {
            GameFrameComposer.BoardUpdatePayload payload = (GameFrameComposer.BoardUpdatePayload) data;
            controller.updateSnapshot(payload.snapshot());
        });

        client.connect("ws://localhost:8080/chess");
    }
}
