package org.example.bus;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Simple synchronous publish/subscribe bus. It's what lets the network
 * layer (which knows the server just sent "JOIN_ACCEPTED") talk to the UI
 * layer (which reacts to it) with neither package importing the other.
 *
 * NOTE ON TYPE SAFETY: this is intentionally still String-keyed with
 * {@code Object} payloads, not a generified {@code EventType<T>} bus. That
 * would be the "more correct" design, and let publish/subscribe fail to
 * compile on a type mismatch instead of throwing a ClassCastException
 * inside a listener at runtime. I didn't make that change here because it
 * would require touching every subscriber across the project -- including
 * UI classes that weren't provided for this refactor -- and getting even
 * one cast wrong elsewhere would break silently. Use {@link GameServerEvents}
 * for the event names, and if you want to take the type-safety step further
 * later, it's a good follow-up once you can update every subscriber at once.
 */
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

    /**
     * Removes a previously-registered listener. You must pass the same
     * {@code Consumer} instance you subscribed with -- keep a reference to
     * it (e.g. a field) if you'll need to unsubscribe later, since a fresh
     * lambda expression is a different object and won't match.
     *
     * Added because every subscriber in this codebase was permanent (never
     * unsubscribed), which is fine for singletons that live for the whole
     * app but leaks listeners for anything created and torn down per-game
     * or per-window -- e.g. GameWindowBusBridge, if a window is ever closed
     * and a new one opened, would otherwise keep the old window updating
     * forever.
     */
    public void unsubscribe(String eventType, Consumer<Object> listener) {
        List<Consumer<Object>> eventListeners = listeners.get(eventType);
        if (eventListeners != null) {
            eventListeners.remove(listener);
        }
    }

    public void publish(String eventType, Object data) {
        List<Consumer<Object>> eventListeners = listeners.get(eventType);
        if (eventListeners != null) {
            for (Consumer<Object> listener : eventListeners) {
                try {
                    listener.accept(data);
                } catch (Exception e) {
                    System.err.println("Error handling event " + eventType + ": " + e.getMessage());
                }
            }
        }
    }
}
