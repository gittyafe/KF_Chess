package network.server;

import org.example.network.server.game.DisconnectCountdownManager;
import org.example.network.server.room.RoomMessenger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The 20s grace period is a private constant, so instead of sleeping in
 * tests we mock the ScheduledExecutorService, capture the scheduled
 * Runnable, and invoke it ourselves to simulate the timer firing.
 */
class DisconnectCountdownManagerTest {

    private ScheduledExecutorService scheduler;
    private RoomMessenger messenger;
    private BiConsumer<String, String> onTimeout;
    private DisconnectCountdownManager manager;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        scheduler = mock(ScheduledExecutorService.class);
        messenger = mock(RoomMessenger.class);
        onTimeout = mock(BiConsumer.class);
        manager = new DisconnectCountdownManager(scheduler, messenger, onTimeout);
    }

    @Test
    void startCountdown_broadcastsCountdown_andSchedulesTimeout() {
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future).when(scheduler).schedule(any(Runnable.class), eq(20L), eq(TimeUnit.SECONDS));

        manager.startCountdown("alice", "bob");

        verify(messenger).broadcastDisconnectCountdown(20, "alice");
        verify(scheduler).schedule(any(Runnable.class), eq(20L), eq(TimeUnit.SECONDS));
    }

    @Test
    void timerFiring_invokesOnTimeoutWithWinnerAndLoser() {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future).when(scheduler).schedule(captor.capture(), eq(20L), eq(TimeUnit.SECONDS));

        manager.startCountdown("alice", "bob");
        captor.getValue().run(); // simulate the scheduler firing after 20s

        verify(onTimeout).accept("alice", "bob");
    }

    @Test
    void cancel_beforeTimerFires_cancelsFutureAndBroadcastsCancellation() {
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        when(future.isDone()).thenReturn(false);
        doReturn(future).when(scheduler).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));

        manager.startCountdown("alice", "bob");
        manager.cancel();

        verify(future).cancel(false);
        verify(messenger).broadcastDisconnectCancelled();
    }

    @Test
    void cancel_afterTimerAlreadyDone_doesNotBroadcastCancellation() {
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        when(future.isDone()).thenReturn(true);
        doReturn(future).when(scheduler).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));

        manager.startCountdown("alice", "bob");
        manager.cancel();

        verify(future, never()).cancel(anyBoolean());
        verify(messenger, never()).broadcastDisconnectCancelled();
    }

    @Test
    void cancel_withoutAnyCountdownStarted_doesNotThrow() {
        assertDoesNotThrow(() -> manager.cancel());
        verifyNoInteractions(messenger);
    }
}
