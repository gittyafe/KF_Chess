package org.example.bus;

import java.util.*;
import java.util.function.Consumer;

public class GameEventBus {
    // מנגנון Singleton - מנוע יחיד לכל האפליקציה
    private static final GameEventBus instance = new GameEventBus();

    // מפה שמחזיקה לכל סוג אירוע (String) רשימה של פונקציות להפעלה (Listeners)
    private final Map<String, List<Consumer<Object>>> listeners = new HashMap<>();

    private GameEventBus() {}

    public static GameEventBus getInstance() {
        return instance;
    }

    /** רכיב שנרשם לקבלת עדכונים (Sub) */
    public synchronized void subscribe(String eventType, Consumer<Object> listener) {
        listeners.computeIfAbsent(eventType, k -> new ArrayList<>()).add(listener);
    }

    /** שליחת אירוע והפצה לכל הרשומים (Pub) */
    public synchronized void publish(String eventType, Object data) {
        List<Consumer<Object>> eventListeners = listeners.get(eventType);
        if (eventListeners != null) {
            for (Consumer<Object> listener : eventListeners) {
                listener.accept(data); // מפעיל את הפונקציה של הרכיב המאזין
            }
        }
    }
}