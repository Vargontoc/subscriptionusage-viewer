package es.vargontoc.subscriptionviewer.usage;

import es.vargontoc.subscriptionviewer.config.ConfigurationProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de {@link UsagePoller}.
 */
class UsagePollerTest {

    private static final String CLAUDE = "claude";
    private static final String OTHER = "other";
    private static final Instant NOW = Instant.parse("2026-10-16T10:00:00Z");

    @Test
    void shouldScheduleCycleWithConfiguredInterval() {
        ConfigurationProvider config = mock(ConfigurationProvider.class);
        when(config.getUsagePollInterval()).thenReturn(Duration.ofSeconds(10));
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        when(executor.scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class)))
                .thenReturn(mock(ScheduledFuture.class));

        UsagePoller poller = new UsagePoller(config, List.of(), mock(UsageHistory.class),
                mock(UsageSnapshotPublisher.class), Clock.systemUTC(), executor);
        poller.start();

        ArgumentCaptor<Long> initialDelay = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> period = ArgumentCaptor.forClass(Long.class);
        verify(executor).scheduleWithFixedDelay(any(Runnable.class), initialDelay.capture(), period.capture(), eq(TimeUnit.MILLISECONDS));
        assertThat(initialDelay.getValue()).isEqualTo(10_000L);
        assertThat(period.getValue()).isEqualTo(10_000L);
    }

    @Test
    void shouldPollAllSourcesAndPublishResults() {
        ConfigurationProvider config = mock(ConfigurationProvider.class);
        when(config.getUsagePollInterval()).thenReturn(Duration.ofSeconds(10));
        UsageHistory history = mock(UsageHistory.class);
        UsageSnapshotPublisher publisher = mock(UsageSnapshotPublisher.class);

        UsageSource claudeSource = fixedSource(CLAUDE, 40, 15);
        UsageSource otherSource = fixedSource(OTHER, 10, 5);

        UsagePoller poller = new UsagePoller(config, List.of(claudeSource, otherSource), history, publisher,
                Clock.fixed(NOW, ZoneId.systemDefault()), Executors.newSingleThreadScheduledExecutor());

        poller.runCycle();

        verify(publisher).publish(CLAUDE, Optional.of(claudeSource.read().get()));
        verify(publisher).publish(OTHER, Optional.of(otherSource.read().get()));
    }

    @Test
    void shouldTimeoutSlowSourceAndContinueWithOthers() {
        ConfigurationProvider config = mock(ConfigurationProvider.class);
        when(config.getUsagePollInterval()).thenReturn(Duration.ofSeconds(10));
        UsageHistory history = mock(UsageHistory.class);
        UsageSnapshotPublisher publisher = mock(UsageSnapshotPublisher.class);

        UsageSource slowSource = new UsageSource() {
            @Override
            public String getProviderId() {
                return CLAUDE;
            }

            @Override
            public Optional<UsageReading> doRead() throws Exception {
                Thread.sleep(6_000);
                return Optional.of(readingOf(CLAUDE, 40, 15));
            }
        };
        UsageSource fastSource = fixedSource(OTHER, 10, 5);

        UsagePoller poller = new UsagePoller(config, List.of(slowSource, fastSource), history, publisher,
                Clock.fixed(NOW, ZoneId.systemDefault()), Executors.newSingleThreadScheduledExecutor());

        long start = System.currentTimeMillis();
        poller.runCycle();
        long elapsed = System.currentTimeMillis() - start;

        verify(publisher).publish(CLAUDE, Optional.empty());
        verify(publisher).publish(OTHER, Optional.of(fastSource.read().get()));
        assertThat(elapsed).isLessThan(6_000L);
    }

    @Test
    void shouldNotRunOnEdtThread() {
        ConfigurationProvider config = mock(ConfigurationProvider.class);
        when(config.getUsagePollInterval()).thenReturn(Duration.ofSeconds(10));
        UsageSnapshotPublisher publisher = mock(UsageSnapshotPublisher.class);

        UsagePoller poller = new UsagePoller(config, List.of(fixedSource(CLAUDE, 40, 15)),
                mock(UsageHistory.class), publisher, Clock.fixed(NOW, ZoneId.systemDefault()),
                Executors.newSingleThreadScheduledExecutor());

        poller.runCycle();

        ArgumentCaptor<Optional<UsageReading>> captor = ArgumentCaptor.forClass(Optional.class);
        verify(publisher).publish(eq(CLAUDE), captor.capture());
        assertThat(captor.getValue()).isPresent();
    }

    @Test
    void shouldStopWithoutSchedulingNewTasks() {
        ConfigurationProvider config = mock(ConfigurationProvider.class);
        when(config.getUsagePollInterval()).thenReturn(Duration.ofSeconds(10));
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);

        UsagePoller poller = new UsagePoller(config, List.of(), mock(UsageHistory.class),
                mock(UsageSnapshotPublisher.class), Clock.systemUTC(), executor);
        poller.start();
        poller.stop();

        verify(executor).shutdown();
    }

    @Test
    void shouldBeIdempotentWhenStoppedTwice() {
        ConfigurationProvider config = mock(ConfigurationProvider.class);
        when(config.getUsagePollInterval()).thenReturn(Duration.ofSeconds(10));
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);

        UsagePoller poller = new UsagePoller(config, List.of(), mock(UsageHistory.class),
                mock(UsageSnapshotPublisher.class), Clock.systemUTC(), executor);
        poller.start();
        poller.stop();
        poller.stop();

        verify(executor).shutdown();
    }

    @Test
    void shouldStopQuicklyWithSlowSourceInProgress() {
        ConfigurationProvider config = mock(ConfigurationProvider.class);
        when(config.getUsagePollInterval()).thenReturn(Duration.ofSeconds(10));
        UsageHistory history = mock(UsageHistory.class);
        UsageSnapshotPublisher publisher = mock(UsageSnapshotPublisher.class);

        UsageSource slowSource = new UsageSource() {
            @Override
            public String getProviderId() {
                return CLAUDE;
            }

            @Override
            public Optional<UsageReading> doRead() throws Exception {
                Thread.sleep(10_000);
                return Optional.of(readingOf(CLAUDE, 40, 15));
            }
        };

        UsagePoller poller = new UsagePoller(config, List.of(slowSource), history, publisher,
                Clock.fixed(NOW, ZoneId.systemDefault()), Executors.newSingleThreadScheduledExecutor());

        Thread cycleThread = new Thread(poller::runCycle);
        cycleThread.start();

        // Dar tiempo a que el ciclo entre en la fuente lenta.
        sleep(100);

        long start = System.currentTimeMillis();
        poller.stop();
        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed).isLessThan(6_000L);
        assertThat(poller.isRunning()).isFalse();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private UsageSource fixedSource(String providerId, int fiveHour, int sevenDay) {
        return new UsageSource() {
            @Override
            public String getProviderId() {
                return providerId;
            }

            @Override
            public Optional<UsageReading> doRead() {
                return Optional.of(readingOf(providerId, fiveHour, sevenDay));
            }
        };
    }

    private UsageReading readingOf(String providerId, int fiveHour, int sevenDay) {
        return new UsageReading(
                providerId,
                NOW,
                List.of(
                        new UsageWindowReading(UsageWindowType.FIVE_HOUR, fiveHour, NOW.plusSeconds(3600)),
                        new UsageWindowReading(UsageWindowType.SEVEN_DAY, sevenDay, NOW.plusSeconds(86400))
                )
        );
    }
}
