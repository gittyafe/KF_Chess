package org.example.network;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChessWebSocketHandler chessHandler;

    public WebSocketConfig() {
        // ההנדלר אינו דורש יותר GameEngine בבנאי שלו כי הוא מנווט דינמית לחדרים!
        this.chessHandler = new ChessWebSocketHandler();
    }

    @Bean
    public ChessWebSocketHandler chessWebSocketHandler() {
        return this.chessHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(this.chessHandler, "/chess")
                .setAllowedOrigins("*");
    }
}