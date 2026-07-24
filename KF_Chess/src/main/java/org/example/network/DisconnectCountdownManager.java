package org.example.network;

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

    public synchronized void startCountdown(String winner, String loser) {
        messenger.broadcastDisconnectCountdown(GRACE_PERIOD_SECONDS, winner);
        timerHandle = scheduler.schedule(() -> {
            System.out.println("Player timed out. Winner: " + winner);
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
