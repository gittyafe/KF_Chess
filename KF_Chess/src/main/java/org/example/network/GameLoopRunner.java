package org.example.network;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Owns the periodic board-tick loop for a room. Knows nothing about
 * networking, players, or game-over rules -- it just fires {@code onTick}
 * on a fixed schedule and reports start/stop to the console. Whatever
 * "advance the engine and broadcast" means is entirely up to the caller.
 */
public class GameLoopRunner {

    private static final long TICK_MS = 30;

    private final ScheduledExecutorService scheduler;
    private final Runnable onTick;
    private volatile ScheduledFuture<?> loopHandle;

    public GameLoopRunner(ScheduledExecutorService scheduler, Runnable onTick) {
        this.scheduler = scheduler;
        this.onTick = onTick;
    }

    public synchronized void start(String roomId) {
        if (scheduler.isShutdown() || (loopHandle != null && !loopHandle.isDone())) return;

        System.out.println("Room [" + roomId + "] Game Loop Started!");
        loopHandle = scheduler.scheduleAtFixedRate(() -> {
            try {
                onTick.run();
            } catch (Exception e) {
                System.err.println("Error in room loop [" + roomId + "]: " + e.getMessage());
            }
        }, 0, TICK_MS, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop(String roomId) {
        if (loopHandle != null) {
            loopHandle.cancel(false);
            System.out.println("Room [" + roomId + "] Game Loop Ended.");
        }
    }
}
