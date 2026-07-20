package org.example.network;

import org.example.engines.GameEngine;
import org.example.models.Board;
import org.example.realtime.RealTimeArbiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import jakarta.annotation.PostConstruct;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GameEngine gameEngine;
    private final ChessWebSocketHandler chessHandler;

    // 🏗️ הבנאי מייצר את האובייקטים פעם אחת בלבד בעליית השרת
    public WebSocketConfig() {
        Board board = new Board(8, 8);
        RealTimeArbiter rta = new RealTimeArbiter();

        this.gameEngine = new GameEngine(board, rta);
        this.chessHandler = new ChessWebSocketHandler(this.gameEngine);
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