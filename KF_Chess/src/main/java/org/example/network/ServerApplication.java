package org.example.network;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ServerApplication {
    public static void main(String[] args) {
        // השורה הזו מפעילה את Spring Boot ואת ה-WebSocketConfig שלך!
        SpringApplication.run(ServerApplication.class, args);
    }
}