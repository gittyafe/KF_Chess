package org.example.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;
import java.util.concurrent.*;

public class MatchmakingManager {

    private final int RANGE_ELO = 100;
    private  final long MINUTE = 60 * 1000; // 60,000 milliseconds

    private static class QueueEntry {
        final WebSocketSession session;
        final String username;
        final int rating;
        final long joinTimeMs;

        QueueEntry(WebSocketSession session, String username, int rating) {
            this.session = session;
            this.username = username;
            this.rating = rating;
            this.joinTimeMs = System.currentTimeMillis();
        }
    }

    private final List<QueueEntry> queue = new ArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ObjectMapper objectMapper;

    public MatchmakingManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        // מריץ בדיקת התאמות ונקיונות תור כל 2 שניות
        this.scheduler.scheduleAtFixedRate(this::processQueue, 2, 2, TimeUnit.SECONDS);
    }

    public synchronized void addToQueue(WebSocketSession session, String username, int rating) {
        // הסרת חיפושים קודמים של אותו סשן/משתמש אם קיימים
        removeFromQueue(session);

        queue.add(new QueueEntry(session, username, rating));
        System.out.println("🔍 " + username + " (" + rating + " ELO) entered matchmaking queue.");

        sendMessage(session, "{\"type\":\"MATCHMAKING_STARTED\",\"message\":\"Searching for an opponent (±100 ELO)...\"}");
    }

    public synchronized void removeFromQueue(WebSocketSession session) {
        queue.removeIf(entry -> entry.session.equals(session));
    }

    private synchronized void processQueue() {
        long currentTime = System.currentTimeMillis();
        List<QueueEntry> toRemove = new ArrayList<>();

        for (int i = 0; i < queue.size(); i++) {
            QueueEntry player1 = queue.get(i);

            // 1. בדיקת Timeout של 60 שניות (1 דקה)
            if (currentTime - player1.joinTimeMs > MINUTE) {
                sendMessage(player1.session, "{\"type\":\"MATCHMAKING_TIMEOUT\",\"reason\":\"No suitable opponent found within 60 seconds.\"}");
                toRemove.add(player1);
                continue;
            }

            // 2. חיפוש יריב מתאים בטווח של +-100 ELO
            for (int j = i + 1; j < queue.size(); j++) {
                QueueEntry player2 = queue.get(j);

                if (Math.abs(player1.rating - player2.rating) <= RANGE_ELO) {
                    // נמצאה התאמה!
                    createMatch(player1, player2);
                    toRemove.add(player1);
                    toRemove.add(player2);
                    break;
                }
            }
        }

        queue.removeAll(toRemove);
    }

    private void createMatch(QueueEntry p1, QueueEntry p2) {
        String matchRoomId = "match_" + UUID.randomUUID().toString().substring(0, 8);
        System.out.println("⚔️ Match found! Room: " + matchRoomId + " | " + p1.username + " vs " + p2.username);

        // שליחת הודעת התאמה לשני השחקנים
        sendMessage(p1.session, String.format("{\"type\":\"MATCH_FOUND\",\"roomId\":\"%s\",\"opponent\":\"%s\"}", matchRoomId, p2.username));
        sendMessage(p2.session, String.format("{\"type\":\"MATCH_FOUND\",\"roomId\":\"%s\",\"opponent\":\"%s\"}", matchRoomId, p1.username));
    }

    private void sendMessage(WebSocketSession session, String text) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(text));
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to send matchmaking message: " + e.getMessage());
        }
    }
}