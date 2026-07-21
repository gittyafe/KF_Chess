package org.example.bus;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class GameEventBus {

    private static final GameEventBus INSTANCE = new GameEventBus();
    private final Map<String, List<Consumer<Object>>> listeners = new ConcurrentHashMap<>();

    private GameEventBus() {}

    public static GameEventBus getInstance() {
        return INSTANCE;
    }

    public void subscribe(String eventType, Consumer<Object> listener) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public void publish(String eventType, Object data) {
        List<Consumer<Object>> eventListeners = listeners.get(eventType);
        if (eventListeners != null) {
            for (Consumer<Object> listener : eventListeners) {
                try {
                    listener.accept(data);
                } catch (Exception e) {
                    System.err.println("❌ Error handling event " + eventType + ": " + e.getMessage());
                }
            }
        }
    }
}