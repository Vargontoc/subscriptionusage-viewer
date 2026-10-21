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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
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
    void shouldStartFirstCycleImmediately() {
        ConfigurationProvider config = mock(ConfigurationProvider.class);
        when(config.getOpenCodePollInterval()).thenReturn(Duration.ofSeconds(60));
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        when(executor.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                .thenReturn(mock(ScheduledFuture.class));

        UsagePoller poller = new UsagePoller(config, List.of(fixedSource("opencode", 40, 15)), mock(UsageHistory.class),
                mock(UsageSnapshotPublisher.class), Clock.systemUTC(), executor);
        poller.start();

        ArgumentCaptor<Long> initialDelay = ArgumentCaptor.forClass(Long.class);
        verify(executor).schedule(any(Runnable.class), initialDelay.capture(), eq(TimeUnit.MILLISECONDS));
        assertThat(initialDelay.getValue()).isZero();
    }

    @Test
    void shouldRetryFailureWithTheSameIntervalWithoutBackoff() {
        ConfigurationProvider config = mock(ConfigurationProvider.class);
        when(config.getOpenCodePollInterval()).thenReturn(Duration.ofSeconds(10));
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        when(executor.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                .thenReturn(mock(ScheduledFuture.class));
        UsageSource source = new UsageSource() {
            @Override
            public String getProviderId() {
                return "opencode";
            }

            @Override
            public Optional<UsageReading> doRead() {
                return Optional.empty();
            }
        };
        UsagePoller poller = new UsagePoller(config, List.of(source), mock(UsageHistory.class),
                mock(UsageSnapshotPublisher.class), Clock.systemUTC(), executor);

        poller.start();
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).schedule(task.capture(), eq(0L), eq(TimeUnit.MILLISECONDS));
        task.getValue().run();

        ArgumentCaptor<Long> delays = ArgumentCaptor.forClass(Long.class);
        verify(executor, org.mockito.Mockito.timeout(1_000).times(2))
                .schedule(any(Runnable.class), delays.capture(), eq(TimeUnit.MILLISECONDS));
        assertThat(delays.getAllValues()).containsExactly(0L, 10_000L);
    }

    @Test
    void shouldPublishGeminiDataAndScheduleNextCycleAfterCompletion() {
        ConfigurationProvider config = mock(ConfigurationProvider.class);
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        when(executor.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                .thenReturn(mock(ScheduledFuture.class));
        UsageSnapshotPublisher publisher = mock(UsageSnapshotPublisher.class);
        UsageSource gemini = mock(UsageSource.class);
        when(gemini.getProviderId()).thenReturn("gemini");
        when(gemini.read()).thenReturn(UsageReadResult.datos(readingOf("gemini", 40, 15)));
        when(gemini.getPollInterval(config)).thenReturn(Duration.ofSeconds(60));
        when(gemini.getReadTimeout()).thenReturn(Duration.ofSeconds(90));

        UsagePoller poller = new UsagePoller(config, List.of(gemini), mock(UsageHistory.class),
                publisher, Clock.fixed(NOW, ZoneId.systemDefault()), executor);
        try {
            poller.start();
            ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
            verify(executor).schedule(task.capture(), eq(0L), eq(TimeUnit.MILLISECONDS));
            task.getValue().run();

            verify(publisher, timeout(1_000)).publish(eq("gemini"),
                    argThat(result -> result.estado() == UsageReadResult.Estado.DATOS), eq(NOW));
            verify(executor, timeout(1_000)).schedule(any(Runnable.class), eq(60_000L),
                    eq(TimeUnit.MILLISECONDS));
        } finally {
            poller.stop();
        }
    }

    @Test
    void shouldPublishGeminiConsultaFallidaAndWaitForItsInterval() {
        ConfigurationProvider config = mock(ConfigurationProvider.class);
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        when(executor.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                .thenReturn(mock(ScheduledFuture.class));
        UsageSnapshotPublisher publisher = mock(UsageSnapshotPublisher.class);
        UsageSource gemini = mock(UsageSource.class);
        when(gemini.getProviderId()).thenReturn("gemini");
        when(gemini.read()).thenReturn(UsageReadResult.consultaFallida());
        when(gemini.getPollInterval(config)).thenReturn(Duration.ofSeconds(60));
        when(gemini.getReadTimeout()).thenReturn(Duration.ofSeconds(90));

        UsagePoller poller = new UsagePoller(config, List.of(gemini), mock(UsageHistory.class),
                publisher, Clock.fixed(NOW, ZoneId.systemDefault()), executor);
        try {
            poller.start();
            ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
            verify(executor).schedule(task.capture(), eq(0L), eq(TimeUnit.MILLISECONDS));
            task.getValue().run();

            verify(publisher, timeout(1_000)).publish(eq("gemini"),
                    argThat(result -> result.estado() == UsageReadResult.Estado.CONSULTA_FALLIDA), eq(NOW));
            verify(executor, timeout(1_000)).schedule(any(Runnable.class), eq(60_000L),
                    eq(TimeUnit.MILLISECONDS));
        } finally {
            poller.stop();
        }
    }

    @Test
    void shouldIsolateSlowGeminiFromAnotherSource() throws InterruptedException {
        ConfigurationProvider config = mock(ConfigurationProvider.class);
        when(config.getUsagePollInterval()).thenReturn(Duration.ofSeconds(60));
        UsageSnapshotPublisher publisher = mock(UsageSnapshotPublisher.class);
        CountDownLatch slowStarted = new CountDownLatch(1);
        UsageSource slowGemini = mock(UsageSource.class);
        when(slowGemini.getProviderId()).thenReturn("gemini");
        when(slowGemini.getPollInterval(config)).thenReturn(Duration.ofSeconds(60));
        when(slowGemini.getReadTimeout()).thenReturn(Duration.ofSeconds(90));
        when(slowGemini.read()).thenAnswer(invocation -> {
            slowStarted.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return UsageReadResult.consultaFallida();
        });

        UsageSource claude = fixedSource(CLAUDE, 40, 15);
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        UsagePoller poller = new UsagePoller(config, List.of(slowGemini, claude), mock(UsageHistory.class),
                publisher, Clock.fixed(NOW, ZoneId.systemDefault()), executor);
        try {
            poller.start();
            assertThat(slowStarted.await(1, TimeUnit.SECONDS)).isTrue();
            verify(publisher, timeout(1_000)).publish(eq(CLAUDE), any(UsageReadResult.class), eq(NOW));
        } finally {
            poller.stop();
        }
    }

    @Test
    void shouldPublishOpenCodeConsultaFallida() {
        ConfigurationProvider config = mock(ConfigurationProvider.class);
        UsageSnapshotPublisher publisher = mock(UsageSnapshotPublisher.class);
        UsageSource opencode = mock(UsageSource.class);
        when(opencode.getProviderId()).thenReturn("opencode");
        when(opencode.read()).thenReturn(UsageReadResult.consultaFallida());
        when(opencode.getReadTimeout()).thenReturn(Duration.ofSeconds(5));

        UsagePoller poller = new UsagePoller(config, List.of(opencode), mock(UsageHistory.class),
                publisher, Clock.fixed(NOW, ZoneId.systemDefault()),
                Executors.newSingleThreadScheduledExecutor());
        try {
            poller.runCycle();
            verify(publisher).publish(eq("opencode"),
                    argThat(result -> result.estado() == UsageReadResult.Estado.CONSULTA_FALLIDA), eq(NOW));
        } finally {
            poller.stop();
        }
    }

    @Test
    void shouldPublishOpenCodeCycleWithoutDataAsFailure() {
        ConfigurationProvider config = mock(ConfigurationProvider.class);
        UsageHistory history = mock(UsageHistory.class);
        UsageSnapshotPublisher publisher = mock(UsageSnapshotPublisher.class);
        UsageSource source = new UsageSource() {
            @Override
            public String getProviderId() {
                return "opencode";
            }

            @Override
            public Optional<UsageReading> doRead() {
                return Optional.empty();
            }
        };

        UsagePoller poller = new UsagePoller(config, List.of(source), history,
                publisher, Clock.fixed(NOW, ZoneId.systemDefault()),
                Executors.newSingleThreadScheduledExecutor());

        poller.runCycle();

        verify(publisher).publish(eq("opencode"),
                argThat(result -> result.estado() == UsageReadResult.Estado.CONSULTA_FALLIDA), eq(NOW));
        org.mockito.Mockito.verifyNoInteractions(history);
    }

    @Test
    void shouldDeliverOnlySuccessfulCyclesToHistory() {
        ConfigurationProvider config = mock(ConfigurationProvider.class);
        UsageHistory history = mock(UsageHistory.class);
        UsageSnapshotPublisher publisher = mock(UsageSnapshotPublisher.class);
        UsageSource source = fixedSource("opencode", 40, 15);
        UsagePoller poller = new UsagePoller(config, List.of(source), history, publisher,
                Clock.fixed(NOW, ZoneId.systemDefault()),
                Executors.newSingleThreadScheduledExecutor());

        poller.runCycle();

        verify(history).add(any(UsageReading.class));
        verify(publisher).publish(eq("opencode"),
                argThat(result -> result.estado() == UsageReadResult.Estado.DATOS), eq(NOW));
    }

    @Test
    void shouldNotDeliverAbsentCycleToHistory() {
        ConfigurationProvider config = mock(ConfigurationProvider.class);
        UsageHistory history = mock(UsageHistory.class);
        UsageSource source = new UsageSource() {
            @Override public String getProviderId() { return "opencode"; }
            @Override public Optional<UsageReading> doRead() { return Optional.empty(); }
        };
        UsagePoller poller = new UsagePoller(config, List.of(source), history,
                mock(UsageSnapshotPublisher.class), Clock.fixed(NOW, ZoneId.systemDefault()),
                Executors.newSingleThreadScheduledExecutor());

        poller.runCycle();

        org.mockito.Mockito.verifyNoInteractions(history);
    }

    @Test
    void shouldKeepClaudePublishingWhenOpenCodeHasNoData() {
        ConfigurationProvider config = mock(ConfigurationProvider.class);
        UsageHistory history = mock(UsageHistory.class);
        UsageSnapshotPublisher publisher = mock(UsageSnapshotPublisher.class);
        UsageSource claude = fixedSource(CLAUDE, 40, 15);
        UsageSource opencode = new UsageSource() {
            @Override public String getProviderId() { return "opencode"; }
            @Override public Optional<UsageReading> doRead() { return Optional.empty(); }
        };

        UsagePoller poller = new UsagePoller(config, List.of(claude, opencode), history,
                publisher, Clock.fixed(NOW, ZoneId.systemDefault()),
                Executors.newSingleThreadScheduledExecutor());

        poller.runCycle();

        verify(publisher).publish(eq(CLAUDE), any(UsageReadResult.class), eq(NOW));
        verify(publisher).publish(eq("opencode"), any(UsageReadResult.class), eq(NOW));
        verify(history).add(any(UsageReading.class));
    }

    @Test
    void shouldPublishInitialOpenAiFailureInsteadOfHidingIt() {
        ConfigurationProvider config = mock(ConfigurationProvider.class);
        UsageSnapshotPublisher publisher = mock(UsageSnapshotPublisher.class);
        UsageSource openAi = mock(UsageSource.class);
        when(openAi.getProviderId()).thenReturn("openai");
        when(openAi.read()).thenReturn(UsageReadResult.falloTransitorio(new IllegalStateException("credentials")));
        when(openAi.getReadTimeout()).thenReturn(Duration.ofSeconds(5));

        UsagePoller poller = new UsagePoller(config, List.of(openAi), mock(UsageHistory.class), publisher,
                Clock.fixed(NOW, ZoneId.systemDefault()),
                Executors.newSingleThreadScheduledExecutor());
        try {
            poller.runCycle();

            verify(publisher).publish(eq("openai"),
                    argThat(result -> result.estado() == UsageReadResult.Estado.FALLO_TRANSITORIO), eq(NOW));
        } finally {
            poller.stop();
        }
    }

    @Test
    void shouldKeepOpenCodeIsolatedWhenClaudeStateFileIsInvalid() {
        ConfigurationProvider config = mock(ConfigurationProvider.class);
        UsageHistory history = mock(UsageHistory.class);
        UsageSnapshotPublisher publisher = mock(UsageSnapshotPublisher.class);
        UsageSource invalidClaude = new UsageSource() {
            @Override public String getProviderId() { return CLAUDE; }
            @Override public Optional<UsageReading> doRead() { return Optional.empty(); }
        };
        UsageSource opencode = fixedSource("opencode", 30, 10);

        UsagePoller poller = new UsagePoller(config, List.of(invalidClaude, opencode), history,
                publisher, Clock.fixed(NOW, ZoneId.systemDefault()),
                Executors.newSingleThreadScheduledExecutor());

        poller.runCycle();

        verify(publisher).publish(eq(CLAUDE), any(UsageReadResult.class), eq(NOW));
        verify(publisher).publish(eq("opencode"), any(UsageReadResult.class), eq(NOW));
        verify(history).add(any(UsageReading.class));
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

        verify(publisher).publish(eq(CLAUDE), any(UsageReadResult.class), eq(NOW));
        verify(publisher).publish(eq(OTHER), any(UsageReadResult.class), eq(NOW));
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

        verify(publisher).publish(eq(CLAUDE), any(UsageReadResult.class), eq(NOW));
        verify(publisher).publish(eq(OTHER), any(UsageReadResult.class), eq(NOW));
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

        ArgumentCaptor<UsageReadResult> captor = ArgumentCaptor.forClass(UsageReadResult.class);
        verify(publisher).publish(eq(CLAUDE), captor.capture(), eq(NOW));
        assertThat(captor.getValue().datos()).isPresent();
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
    void shouldCloseSourcesEvenWhenNoCycleWasStarted() {
        ConfigurationProvider config = mock(ConfigurationProvider.class);
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        AtomicBoolean closed = new AtomicBoolean();
        class CloseableSource implements UsageSource, AutoCloseable {
            @Override public String getProviderId() { return "opencode"; }
            @Override public Optional<UsageReading> doRead() { return Optional.empty(); }
            public void close() { closed.set(true); }
        }
        UsageSource source = new CloseableSource();

        UsagePoller poller = new UsagePoller(config, List.of(source), mock(UsageHistory.class),
                mock(UsageSnapshotPublisher.class), Clock.systemUTC(), executor);

        poller.stop();

        assertThat(closed.get()).isTrue();
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
