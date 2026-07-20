package org.example.network;

import org.example.engines.GameEngine;
import org.example.models.Board;
import org.example.models.Piece;
import org.example.models.PieceFactory;
import org.example.models.Position;
import org.example.realtime.RealTimeArbiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import jakarta.annotation.PostConstruct;

import java.io.BufferedReader;
import java.io.FileReader;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private static final String BOARD_CSV = "src/main/resources/board.csv";

    private final GameEngine gameEngine;
    private final ChessWebSocketHandler chessHandler;

    // 🏗️ הבנאי מייצר את האובייקטים פעם אחת בלבד בעליית השרת
    public WebSocketConfig() {
        Board board = new Board(8, 8);
        RealTimeArbiter rta = new RealTimeArbiter();

        // ⚠️ בלי זה, ה-board נשאר ריק לגמרי - כל snapshot שהשרת שולח יוצא עם
        // pieces=[] , וה-client (ChessWebSocketClient) מתעלם בכוונה מ-snapshot
        // ריק ("server not ready"). התוצאה: החלון נשאר ריק לנצח כי אף פעם לא
        // מגיע snapshot עם כלים בפועל.
        loadBoardFromCSV(board, BOARD_CSV);

        this.gameEngine = new GameEngine(board, rta);
        this.chessHandler = new ChessWebSocketHandler(this.gameEngine);
    }

    // זהה ללוגיקה שב-MainGUI.loadBoardFromCSV - טוענים את אותו קובץ לוח
    // כדי שהלוח בשרת יתחיל באותו מצב התחלתי כמו במשחק המקומי.
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
            System.err.println("שגיאה בטעינת הלוח מ-CSV בשרת: " + e.getMessage());
        }
    }

    // ⏱️ הלולאה המרכזית - רצה בדיוק פעם אחת ולא משתכפלת לעולם!
    @PostConstruct
    public void startServerGameLoop() {
        new Thread(() -> {
            System.out.println("🚀 לולאת ה-Tick המרכזית של השרת הופעלה בהצלחה!");
            while (!gameEngine.isGameOver()) {
                try {
                    Thread.sleep(30);
                    gameEngine.wait_(30); // קידום שעון המנוע
                    chessHandler.sendGameStateToAll(); // הפצה ברשת
                } catch (Exception e) {
                    System.err.println("שגיאה בלולאת המשחק המרכזית: " + e.getMessage());
                }
            }
        }, "Chess-Game-Loop-Thread").start();
    }

    @Bean
    public GameEngine gameEngine() {
        return this.gameEngine;
    }

    @Bean
    public ChessWebSocketHandler chessWebSocketHandler() {
        return this.chessHandler;
    }

    // 🌐 הרשת רק נרשמת, בלי לייצר ת'רדים או אובייקטים חדשים
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(this.chessHandler, "/chess")
                .setAllowedOrigins("*");
    }
}