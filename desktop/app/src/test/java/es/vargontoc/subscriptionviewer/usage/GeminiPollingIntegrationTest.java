package es.vargontoc.subscriptionviewer.usage;

import es.vargontoc.subscriptionviewer.config.AppProperties;
import es.vargontoc.subscriptionviewer.config.ConfigurationProvider;
import es.vargontoc.subscriptionviewer.usage.gemini.GeminiCliRunner;
import es.vargontoc.subscriptionviewer.usage.gemini.GeminiUsageParser;
import es.vargontoc.subscriptionviewer.usage.gemini.GeminiUsageSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Verifica el cableado entre la fuente real de Gemini y el planificador. */
class GeminiPollingIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
    private static final String VALID_RESPONSE = """
            {"status":"SUCCESS","command":{"data":{"groups":[
              {"name":"Gemini Models","buckets":[
                {"window":"weekly","remaining_fraction":0.5,
                 "reset_time":"2026-10-27T12:00:00Z"}]}
            ]}}}
            """;

    @Test
    void intervaloDeSesentaSegundosHaceLaPrimeraLecturaEnSegundoPlano() throws Exception {
        ScheduledCalls scheduler = new ScheduledCalls();
        AtomicInteger reads = new AtomicInteger();
        GeminiCliRunner runner = mock(GeminiCliRunner.class);
        when(runner.run()).thenAnswer(invocation -> {
            reads.incrementAndGet();
            return new GeminiCliRunner.CliResult(true, VALID_RESPONSE);
        });
        ConfigurationProvider configuration = configuration(Duration.ofSeconds(60));
        GeminiUsageSource source = new GeminiUsageSource(runner, new GeminiUsageParser(), configuration);
        UsagePoller poller = poller(source, scheduler.executor, configuration);

        poller.start();
        assertThat(scheduler.delays()).containsExactly(0L);
        scheduler.runNext();
        awaitValue(reads, 1);

        assertThat(scheduler.delays()).containsExactly(0L, 60_000L);
        scheduler.runNext();
        awaitValue(reads, 2);
        assertThat(scheduler.delays()).containsExactly(0L, 60_000L, 60_000L);
        poller.stop();
    }

    @Test
    void intervaloDeUnSegundoSeLimitaATreintaYNoSuperaDosLecturasPorMinuto() throws Exception {
        ScheduledCalls scheduler = new ScheduledCalls();
        AtomicInteger reads = new AtomicInteger();
        GeminiCliRunner runner = mock(GeminiCliRunner.class);
        when(runner.run()).thenAnswer(invocation -> {
            reads.incrementAndGet();
            return new GeminiCliRunner.CliResult(true, VALID_RESPONSE);
        });
        ConfigurationProvider configuration = configurationWithRawGeminiInterval("1s");
        GeminiUsageSource source = new GeminiUsageSource(runner, new GeminiUsageParser(), configuration);
        assertThat(source.getPollInterval(configuration)).isEqualTo(Duration.ofSeconds(30));
        UsagePoller poller = poller(source, scheduler.executor, configuration);

        poller.start();
        scheduler.runNext();
        awaitValue(reads, 1);
        Thread.sleep(50); // Esperar a que la segunda programación se complete
        assertThat(scheduler.delays()).containsExactly(0L, 30_000L);

        // El siguiente ciclo es el único que puede ocurrir durante el minuto simulado.
        scheduler.runNext();
        awaitValue(reads, 2);
        assertThat(scheduler.delays()).containsExactly(0L, 30_000L, 30_000L);
        poller.stop();
    }

    @Test
    void falloDeGeminiNoDetieneSusCiclosNiAfectaAClaudeOpenCodeUOpenAi() throws Exception {
        ScheduledCalls scheduler = new ScheduledCalls();
        AtomicInteger geminiReads = new AtomicInteger();
        GeminiCliRunner runner = mock(GeminiCliRunner.class);
        when(runner.run()).thenAnswer(invocation -> {
            geminiReads.incrementAndGet();
            return new GeminiCliRunner.CliResult(false, "");
        });
        ConfigurationProvider configuration = configuration(Duration.ofSeconds(30));
        GeminiUsageSource gemini = new GeminiUsageSource(runner, new GeminiUsageParser(), configuration);
        UsageSource claude = fixedSource("claude");
        UsageSource opencode = fixedSource("opencode");
        UsageSource openai = fixedSource("openai");
        UsageSnapshotPublisher publisher = new UsageSnapshotPublisher(Runnable::run);
        UsagePoller poller = new UsagePoller(configuration,
                List.of(gemini, claude, opencode, openai), new UsageHistory(), publisher,
                Clock.fixed(NOW, ZoneOffset.UTC), scheduler.executor);

        poller.start();
        scheduler.runNext();
        scheduler.runNext();
        scheduler.runNext();
        scheduler.runNext();
        awaitValue(geminiReads, 1);
        assertThat(publisher.getCurrentSnapshot().readingOf("claude")).isPresent();
        assertThat(publisher.getCurrentSnapshot().readingOf("opencode")).isPresent();
        assertThat(publisher.getCurrentSnapshot().readingOf("openai")).isPresent();
        assertThat(scheduler.delays()).contains(30_000L);

        scheduler.runNext();
        Thread.sleep(50); // Esperar a que la segunda lectura se complete
        awaitValue(geminiReads, 2);
        assertThat(geminiReads).hasValue(2);
        poller.stop();
    }

    private UsagePoller poller(UsageSource source, ScheduledExecutorService executor,
            ConfigurationProvider configuration) {
        return new UsagePoller(configuration, List.of(source), new UsageHistory(),
                new UsageSnapshotPublisher(Runnable::run), Clock.fixed(NOW, ZoneOffset.UTC), executor);
    }

    private ConfigurationProvider configuration(Duration interval) {
        ConfigurationProvider configuration = mock(ConfigurationProvider.class);
        when(configuration.getGeminiUsagePollInterval()).thenReturn(interval);
        return configuration;
    }

    private ConfigurationProvider configurationWithRawGeminiInterval(String interval) {
        Environment environment = mock(Environment.class);
        when(environment.getProperty("subscription-viewer.gemini.usage-poll-interval"))
                .thenReturn(interval);
        return new ConfigurationProvider(new AppProperties(), environment);
    }

    private UsageSource fixedSource(String provider) {
        return new UsageSource() {
            @Override public String getProviderId() { return provider; }
            @Override public Optional<UsageReading> doRead() {
                return Optional.of(new UsageReading(provider, NOW, List.of(
                        new UsageWindowReading(UsageWindowType.FIVE_HOUR, 10, NOW.plusSeconds(3600)))));
            }
        };
    }

    private void awaitValue(AtomicInteger value, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (value.get() < expected && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertThat(value).hasValue(expected);
    }

    private static final class ScheduledCalls {
        private final List<Runnable> tasks = new ArrayList<>();
        private final List<Long> delays = new ArrayList<>();
        private final ScheduledExecutorService executor = mock(ScheduledExecutorService.class);

        private ScheduledCalls() {
            when(executor.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                    .thenAnswer(invocation -> {
                        tasks.add(invocation.getArgument(0));
                        delays.add(invocation.getArgument(1));
                        return mock(ScheduledFuture.class);
                    });
        }

        private void runNext() {
            Runnable task = tasks.remove(0);
            task.run();
        }

        private List<Long> delays() {
            return List.copyOf(delays);
        }
    }
}
