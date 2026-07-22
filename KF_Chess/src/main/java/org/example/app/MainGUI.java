package org.example.app;

import org.example.bus.GameEventBus;
import org.example.controllers.NetworkController;
import org.example.engines.GameHistoryManager;
import org.example.models.Role;
import org.example.network.ChessWebSocketClient;
import org.example.view.*;

public class MainGUI {
    private static final int BOARD_COLS = 8;
    private static final int BOARD_ROWS = 8;
    private static final int BOARD_SIZE_PX = 650;
    private static final int PIECE_MARGIN_PX = 10;
    private static final String BOARD_IMAGE = "src/main/resources/board.png";
    private static final String SERVER_URL = "ws://localhost:8080/chess";

    public static void main(String[] args) {
        // 1. אתחול מנועים ותשתיות תצוגה
        BoardGeometry geometry = new BoardGeometry(BOARD_SIZE_PX, BOARD_COLS, BOARD_ROWS, PIECE_MARGIN_PX);
        PieceImageLoader imageLoader = new PieceImageLoader(geometry);
        imageLoader.preload();

        ImgRenderer boardRenderer = new ImgRenderer(BOARD_IMAGE, geometry, imageLoader);
        GameHistoryManager historyManager = new GameHistoryManager();
        ScoreManager scoreManager = new ScoreManager();

        ChessWebSocketClient networkClient = new ChessWebSocketClient();
        NetworkController controller = new NetworkController(networkClient, Role.UNKNOWN);

        // 2. רישום מנהל מחזור החיים של המשחק
        GameLifecycleManager lifecycleManager = new GameLifecycleManager(
                geometry, boardRenderer, historyManager, scoreManager, controller
        );
        lifecycleManager.registerEventListeners();

        // 3. יצירת חלון הלובי וחיבורו לשכבת התקשורת
        LobbyWindow lobbyWindow = new LobbyWindow(new LobbyWindow.LobbyEventListener() {
            @Override
            public void onLoginRequested(String username, String password) {
                lifecycleManager.setCurrentUsername(username);

                // מתחברים לשרת ושולחים בקשת LOGIN נקי
                networkClient.connect(SERVER_URL);
                networkClient.sendLogin(username, password);
            }

            @Override
            public void onFindMatchRequested() {
                networkClient.sendFindMatch();
            }

            @Override
            public void onCancelMatchmakingRequested() {
                networkClient.sendCancelMatchmaking();
            }

            @Override
            public void onJoinRoomRequested(String roomId) {
                lifecycleManager.setCurrentRoomId(roomId);
                networkClient.sendJoinRoom(roomId);
            }
            @Override
            public void onCreateRoomRequested() {
//                lifecycleManager.setCurrentRoomId(roomName);
//                networkClient.sendCreateRoom(roomName);
            }
        });

        // 4. הצגת החלון
        lobbyWindow.show();
    }
}