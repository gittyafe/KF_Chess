package org.example.network;

import org.example.engines.GameEngine;
import org.example.engines.GameSnapshot;
import org.example.models.Board;
import org.example.models.Piece;
import org.example.models.PieceFactory;
import org.example.models.Position;
import org.example.realtime.RealTimeArbiter;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GameRoom {
    private final String roomId;
    private final List<WebSocketSession> sessions = new ArrayList<>();
    private final GameEngine gameEngine;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private WebSocketSession whiteSession;
    private String whiteUsername;
    private WebSocketSession blackSession;
    private String blackUsername;
    private boolean isStarted = false;

    public GameRoom(String roomId) {
        this.roomId = roomId;

        // יצירת מנוע נפרד לכל חדר
        Board board = new Board(8, 8);
        RealTimeArbiter rta = new RealTimeArbiter();
        loadBoardFromCSV(board, "src/main/resources/board.csv");
        this.gameEngine = new GameEngine(board, rta);

        // רישום מאזינים מקומיים לחדר זה בלבד
        this.gameEngine.addCaptureListener((capturedType, capturingColor) -> {
            broadcast("{\"type\":\"PIECE_CAPTURED\",\"data\":[\"" + capturedType + "\",\"" + capturingColor + "\"]}");
        });

        this.gameEngine.addMoveListener((time, moveNotation, color) -> {
            broadcast("{\"type\":\"MOVE_LOGGED\",\"data\":[\"" + time + "\",\"" + moveNotation + "\",\"" + color + "\"]}");
        });
    }

    /**
     * הוספת משתמש לחדר:
     * - משתמש 1 -> שחקן לבן
     * - משתמש 2 -> שחקן שחור (מזניק את המשחק)
     * - משתמש 3+ -> צופה (Spectator)
     */
    public synchronized boolean addPlayer(WebSocketSession session, String username) {
        sessions.add(session);

        if (whiteSession == null) {
            whiteSession = session;
            whiteUsername = username;
            System.out.println("👤 Player 1 (White) joined room [" + roomId + "]: " + username);
        } else if (blackSession == null) {
            blackSession = session;
            blackUsername = username;
            isStarted = true;
            System.out.println("👤 Player 2 (Black) joined room [" + roomId + "]: " + username);

            broadcastGameStarted();
            startLoop();
        } else {
            System.out.println("👁️ Spectator joined room [" + roomId + "]: " + username);

            // אם המשחק כבר פעיל, שולחים לצופה את המצב הנוכחי באופן מיידי
            if (isStarted) {
                sendGameStateToSession(session);
            }
        }

        return true;
    }

    private void broadcastGameStarted() {
        try {
            String message = objectMapper.writeValueAsString(Map.of(
                    "type", "GAME_STARTED",
                    "data", List.of(whiteUsername, blackUsername)
            ));
            broadcast(message);
        } catch (Exception e) {
            System.err.println("Error broadcasting GAME_STARTED: " + e.getMessage());
        }
    }

    /**
     * שליחת תמונת מצב ראשונית לצופה שנכנס באמצע המשחק
     */
    private void sendGameStateToSession(WebSocketSession session) {
        new Thread(() -> {
            try {
                // 1. שליחת הודעת GAME_STARTED לצופה כדי שיפתח אצלו חלון המשחק
                String gameStartedJson = objectMapper.writeValueAsString(Map.of(
                        "type", "GAME_STARTED",
                        "data", List.of(whiteUsername, blackUsername)
                ));
                session.sendMessage(new TextMessage(gameStartedJson));

                // השהיה קצרה (100ms) המבטיחה שהלקוח יסיים ליזום את החלון לפני קבלת ה-Snapshot
                Thread.sleep(100);

                // 2. שליחת תמונת הלוח העדכנית
                GameSnapshot snapshot = gameEngine.getSnapshot();
                String snapshotJson = objectMapper.writeValueAsString(snapshot);
                session.sendMessage(new TextMessage("{\"type\":\"BOARD_UPDATE\",\"snapshot\":" + snapshotJson + "}"));
            } catch (Exception e) {
                System.err.println("Error sending state to spectator: " + e.getMessage());
            }
        }).start();
    }

    public void startLoop() {
        new Thread(() -> {
            System.out.println("🚀 Room [" + roomId + "] Game Loop Started!");
            while (!gameEngine.isGameOver() && isRoomActive()) {
                try {
                    Thread.sleep(30);
                    gameEngine.wait_(30);
                    sendGameStateToAll();
                } catch (Exception e) {
                    System.err.println("Error in room loop [" + roomId + "]: " + e.getMessage());
                }
            }
            System.out.println("🏁 Room [" + roomId + "] Game Loop Ended.");
        }, "Loop-Room-" + roomId).start();
    }

    public void sendGameStateToAll() throws Exception {
        GameSnapshot snapshot = gameEngine.getSnapshot();
        String snapshotJson = objectMapper.writeValueAsString(snapshot);
        broadcast("{\"type\":\"BOARD_UPDATE\",\"snapshot\":" + snapshotJson + "}");
    }

    public void broadcast(String messageText) {
        TextMessage msg = new TextMessage(messageText);
        // יצירת העתק של הרשימה למניעת ConcurrentModificationException
        for (WebSocketSession session : new ArrayList<>(sessions)) {
            try {
                if (session.isOpen()) session.sendMessage(msg);
            } catch (Exception e) {
                // התעלמות משגיאות שליחה לסשן ספציפי שנותק
            }
        }
    }

    private boolean isRoomActive() {
        return (whiteSession != null && whiteSession.isOpen()) || (blackSession != null && blackSession.isOpen());
    }

    private void loadBoardFromCSV(Board board, String csvPath) {
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
            System.err.println("Error loading CSV: " + e.getMessage());
        }
    }

    // Getters
    public GameEngine getGameEngine() { return gameEngine; }
    public boolean isStarted() { return isStarted; }
    public String getWhiteUsername() { return whiteUsername; }
    public String getBlackUsername() { return blackUsername; }
    public List<WebSocketSession> getSessions() { return sessions; }
}