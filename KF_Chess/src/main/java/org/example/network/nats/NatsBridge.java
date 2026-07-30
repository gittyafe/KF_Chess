package org.example.network.nats;

import io.nats.client.Connection;
import io.nats.client.ConnectionListener;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.Dispatcher;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.BiConsumer;

@Slf4j
public class NatsBridge {

    private static final String NATS_URL = System.getenv().getOrDefault("NATS_URL", "nats://localhost:4222");

    private static volatile Connection nc;

    private NatsBridge() {
    }

    public static void init() {
        ensureInitialized();
    }

    private static Connection ensureInitialized() {
        Connection connection = nc;
        if (connection != null) {
            return connection;
        }
        synchronized (NatsBridge.class) {
            if (nc == null) {
                connect();
            }
            return nc;
        }
    }

    private static void connect() {
        try {
            Options options = new Options.Builder()
                    .server(NATS_URL)
                    .maxReconnects(-1)
                    .reconnectWait(Duration.ofSeconds(1))
                    .connectionListener(NatsBridge::onConnectionEvent)
                    .build();
            nc = Nats.connect(options);
            log.info("[NATS] Connected successfully to {}", NATS_URL);
        } catch (IOException | InterruptedException e) {
            log.error("[NATS ERROR] Failed to connect to NATS at {}. Publish/subscribe calls will keep " +
                    "retrying this connection lazily on every call until it succeeds.", NATS_URL, e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void onConnectionEvent(Connection connection, ConnectionListener.Events type) {
        switch (type) {
            case DISCONNECTED -> log.warn("[NATS] Disconnected from {} -- will keep retrying.", NATS_URL);
            case RECONNECTED -> log.info("[NATS] Reconnected to {}", NATS_URL);
            case CLOSED -> log.error("[NATS] Connection permanently closed (gave up reconnecting).");
            default -> { /* RESUBSCRIBED, DISCOVERED_SERVERS, etc. -- not actionable here */ }
        }
    }

    public static boolean isConnected() {
        Connection connection = nc;
        return connection != null && connection.getStatus() == Connection.Status.CONNECTED;
    }

    public static void publish(String subject, String message) {
        Connection connection = ensureInitialized();
        if (connection == null) {
            log.error("[NATS] Dropped publish to '{}': no connection could be established.", subject);
            return;
        }
        connection.publish(subject, message.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Broadcast subscribe: EVERY process that calls this for the same
     * subject gets EVERY message. Correct only for "receive everything,
     * no-op if it's not mine locally" consumers -- game.commands.*,
     * game.events.*, gateway.outbound.*, gateway.session.closed. For a
     * request that exactly one service instance should handle (auth,
     * room creation, matchmaking), use {@link #subscribeQueue} instead --
     * plain subscribe() on those means every horizontally-scaled instance
     * of that service processes the same request redundantly.
     */
    public static void subscribe(String subject, BiConsumer<String, String> handler) {
        subscribeInternal(subject, null, handler);
    }

    /**
     * Queue-group subscribe: all processes that call this with the same
     * {@code queueGroup} name form one logical work pool -- NATS delivers
     * each message to exactly one member, so scaling that service to N
     * instances load-balances requests instead of duplicating them. Use
     * for request/response-style consumers: auth.requests.*,
     * room.requests.*, matchmaking.requests.*.
     */
    public static void subscribeQueue(String subject, String queueGroup, BiConsumer<String, String> handler) {
        subscribeInternal(subject, queueGroup, handler);
    }

    private static void subscribeInternal(String subject, String queueGroup, BiConsumer<String, String> handler) {
        Connection connection = ensureInitialized();
        if (connection == null) {
            log.error("[NATS] Cannot subscribe to '{}': no connection could be established.", subject);
            return;
        }
        Dispatcher dispatcher = connection.createDispatcher((msg) -> {
            String messageText = new String(msg.getData(), StandardCharsets.UTF_8);
            try {
                handler.accept(msg.getSubject(), messageText);
            } catch (Exception e) {
                log.error("[NATS] Handler threw while processing subject '{}': {}", msg.getSubject(), e.getMessage(), e);
            }
        });
        if (queueGroup != null) {
            dispatcher.subscribe(subject, queueGroup);
        } else {
            dispatcher.subscribe(subject);
        }
    }

    public static synchronized void shutdown() {
        Connection connection = nc;
        if (connection != null) {
            try {
                connection.close();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                nc = null;
            }
        }
    }
}