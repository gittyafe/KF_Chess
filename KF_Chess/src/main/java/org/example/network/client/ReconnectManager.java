package org.example.network.client;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Owns every piece of state around *automatically* reconnecting a dropped
 * socket: whether we're allowed to try, how many attempts we've made, and
 * the scheduler driving the retry loop. Extracted whole from
 * ChessWebSocketClient, where these five fields (hasLoggedInBefore,
 * reconnecting, the scheduler, and the two retry constants) and their two
 * methods were interleaved with connection and message-parsing code that
 * has nothing to do with retry timing.
 *
 * ChessWebSocketClient only ever tells this class two things: "we just
 * connected" ({@link #reset()} / {@link #markLoggedIn()}) and "we just got
 * disconnected" ({@link #onDisconnected}); this class decides what happens
 * next and calls back through the two collaborators passed to its
 * constructor. Retry timing, attempt counting, and give-up behavior are
 * unchanged from the original: 2-second delay, 10 attempts (~20s, matching
 * the server's disconnect grace window), then a RECONNECT_REJECTED event.
 */
final class ReconnectManager {

    private static final int RETRY_DELAY_SECONDS = 2;
    private static final int MAX_ATTEMPTS = 10;

    private final Supplier<Boolean> isSocketOpen;
    private final Supplier<CompletableFuture<Void>> connectAttempt;
    private final Runnable onGiveUp;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "chess-reconnect");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean hasLoggedInBefore = false;
    private volatile boolean reconnecting = false;

    /**
     * @param isSocketOpen   reports whether the live socket is currently usable,
     *                       so a retry attempt can bail out if we reconnected
     *                       through some other path in the meantime
     * @param connectAttempt kicks off one connection attempt and returns a
     *                       future that completes once the socket is assigned
     *                       (or fails if the attempt itself failed)
     * @param onGiveUp       called once after MAX_ATTEMPTS is exceeded
     */
    ReconnectManager(Supplier<Boolean> isSocketOpen,
                      Supplier<CompletableFuture<Void>> connectAttempt,
                      Runnable onGiveUp) {
        this.isSocketOpen = isSocketOpen;
        this.connectAttempt = connectAttempt;
        this.onGiveUp = onGiveUp;
    }

    /** Marks that a real login has succeeded at least once, which is what
     *  distinguishes "first connect, about to log in" from "we were mid-game
     *  and dropped" for {@link #onDisconnected}. */
    void markLoggedIn() {
        hasLoggedInBefore = true;
    }

    boolean hasLoggedInBefore() {
        return hasLoggedInBefore;
    }

    /** Call on every successful onOpen -- clears the "currently retrying" flag. */
    void reset() {
        reconnecting = false;
    }

    /**
     * Call from onClose/onError. Starts a retry loop only if we'd logged in
     * before and still have credentials to reconnect with; a no-op if a
     * retry loop is already running, so it's safe to call from both
     * callbacks without double-scheduling.
     *
     * @param hasCredentials whether there's a username/password on hand to
     *                       reconnect with (mirrors the original's
     *                       {@code currentUsername != null} check)
     */
    synchronized void onDisconnected(boolean hasCredentials) {
        if (hasLoggedInBefore && hasCredentials && !reconnecting) {
            reconnecting = true;
            scheduleAttempt(1);
        }
    }

    private void scheduleAttempt(int attempt) {
        if (attempt > MAX_ATTEMPTS) {
            System.err.println("Giving up reconnecting after " + MAX_ATTEMPTS + " attempts.");
            reconnecting = false;
            onGiveUp.run();
            return;
        }
        scheduler.schedule(() -> {
            System.out.println("Reconnect attempt " + attempt + "/" + MAX_ATTEMPTS + "...");
            if (isSocketOpen.get()) {
                // Already reconnected via another path.
                reconnecting = false;
                return;
            }
            try {
                connectAttempt.get().exceptionally(ex -> {
                    scheduleAttempt(attempt + 1);
                    return null;
                });
            } catch (Exception e) {
                scheduleAttempt(attempt + 1);
            }
        }, RETRY_DELAY_SECONDS, TimeUnit.SECONDS);
    }
}
