package org.example.network.server.game;

import lombok.extern.slf4j.Slf4j;
import org.example.network.server.room.RoomMessenger;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Runs the grace-period countdown after a player disconnects mid-game,
 * giving them a window to reconnect before the game is auto-resigned in
 * their opponent's favor. Reports the outcome (winner, loser) upward
 * through a callback instead of knowing how to end a game itself.
 */
@Slf4j
public class DisconnectCountdownManager {

    private static final int GRACE_PERIOD_SECONDS = 20;

    private final ScheduledExecutorService scheduler;
    private final RoomMessenger messenger;
    private final BiConsumer<String, String> onTimeout; // (winner, loser)

    private volatile ScheduledFuture<?> timerHandle;

    public DisconnectCountdownManager(ScheduledExecutorService scheduler, RoomMessenger messenger,
                                       BiConsumer<String, String> onTimeout) {
        this.scheduler = scheduler;
        this.messenger = messenger;
        this.onTimeout = onTimeout;
    }

    /**
     * Starts (or restarts) the countdown.
     *
     * <p><b>Fixed bug:</b> if this was called twice without a
     * {@link #cancel()} in between -- e.g. a flaky connection disconnects,
     * then disconnects again before the first countdown finishes --
     * {@code timerHandle} was simply overwritten with the new
     * {@code ScheduledFuture}. The *first* timer was never cancelled and
     * kept running in the background. If a reconnect later called
     * {@link #cancel()}, it only cancelled whichever timer was
     * *currently* referenced (the second one); the first, orphaned timer
     * would still fire ~20s after its own start and auto-resign the game
     * out from under a player who had already reconnected. Any existing
     * timer is now explicitly cancelled before a new one is scheduled.
     */
    public synchronized void startCountdown(String winner, String loser) {
        if (timerHandle != null && !timerHandle.isDone()) {
            timerHandle.cancel(false); // superseded by this new countdown, not a "cancel" the room should announce
        }

        messenger.broadcastDisconnectCountdown(GRACE_PERIOD_SECONDS, winner);
        timerHandle = scheduler.schedule(() -> {
            log.info("[SERVER OUT] Player timed out. Winner: {}", winner);
            onTimeout.accept(winner, loser);
        }, GRACE_PERIOD_SECONDS, TimeUnit.SECONDS);
    }

    public synchronized void cancel() {
        if (timerHandle != null && !timerHandle.isDone()) {
            timerHandle.cancel(false);
            messenger.broadcastDisconnectCancelled();
        }
    }
}
