package es.vargontoc.subscriptionviewer.usage.opencode;

import es.vargontoc.subscriptionviewer.config.ConfigurationProvider;
import es.vargontoc.subscriptionviewer.usage.UsageReadResult;
import es.vargontoc.subscriptionviewer.usage.UsageReading;
import es.vargontoc.subscriptionviewer.usage.UsageWindowReading;
import es.vargontoc.subscriptionviewer.usage.UsageWindowType;
import es.vargontoc.subscriptionviewer.usage.UsageSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

/** Fuente atómica de OpenCode: una lectura está formada por tres consultas CLI. */
@Component
public final class OpenCodeUsageSource implements UsageSource, AutoCloseable {
    public static final String PROVIDER_ID = "opencode";

    private final OpenCodeCliRunner runner;
    private final OpenCodeStatsParser parser;
    private final UsageWindowRanges ranges;
    private final OpenCodeUsageCalculator calculator;
    private final ExecutorService pool;

    /** Compatibilidad de construcción para clientes que solo inyectaban Jackson. */
    public OpenCodeUsageSource(ObjectMapper objectMapper) {
        this(new OpenCodeCliRunner(java.time.Duration.ofSeconds(30)),
                new OpenCodeStatsParser(objectMapper),
                new UsageWindowRanges(Clock.systemDefaultZone(), ZoneId.systemDefault(), 1),
                new OpenCodeUsageCalculator());
    }

    public OpenCodeUsageSource(ObjectMapper objectMapper, ConfigurationProvider configurationProvider) {
        this(new OpenCodeCliRunner(configurationTimeout(configurationProvider)),
                new OpenCodeStatsParser(objectMapper),
                new UsageWindowRanges(Clock.systemDefaultZone(), ZoneId.systemDefault(),
                        configurationProvider.getOpenCodeCycleDay()),
                new OpenCodeUsageCalculator(configurationProvider));
    }

    private static java.time.Duration configurationTimeout(ConfigurationProvider configurationProvider) {
        java.time.Duration timeout = configurationProvider.getOpenCodeCommandTimeout();
        return timeout == null ? java.time.Duration.ofSeconds(30) : timeout;
    }

    @Autowired
    public OpenCodeUsageSource(ConfigurationProvider configurationProvider,
                               OpenCodeCliRunner runner, OpenCodeStatsParser parser) {
        this(runner, parser, new UsageWindowRanges(Clock.systemDefaultZone(),
                ZoneId.systemDefault(), configurationProvider.getOpenCodeCycleDay()),
                new OpenCodeUsageCalculator(configurationProvider));
    }

    /** Constructor de pruebas: conserva el pool productivo de tres hilos demonio. */
    public OpenCodeUsageSource(OpenCodeCliRunner runner, OpenCodeStatsParser parser,
                               UsageWindowRanges ranges) {
        this(runner, parser, ranges, newPool());
    }

    /** Constructor de pruebas que permite observar el planificador de tareas. */
    public OpenCodeUsageSource(OpenCodeCliRunner runner, OpenCodeStatsParser parser,
                               UsageWindowRanges ranges, ExecutorService pool) {
        this(runner, parser, ranges, new OpenCodeUsageCalculator(), pool);
    }

    public OpenCodeUsageSource(OpenCodeCliRunner runner, OpenCodeStatsParser parser,
                               UsageWindowRanges ranges, OpenCodeUsageCalculator calculator) {
        this(runner, parser, ranges, calculator, newPool());
    }

    /** Constructor de pruebas que permite inyectar la tabla de topes. */
    public OpenCodeUsageSource(OpenCodeCliRunner runner, OpenCodeStatsParser parser,
                               UsageWindowRanges ranges, OpenCodeUsageCalculator calculator,
                               ExecutorService pool) {
        this.runner = java.util.Objects.requireNonNull(runner, "runner");
        this.parser = java.util.Objects.requireNonNull(parser, "parser");
        this.ranges = java.util.Objects.requireNonNull(ranges, "ranges");
        this.calculator = java.util.Objects.requireNonNull(calculator, "calculator");
        this.pool = java.util.Objects.requireNonNull(pool, "pool");
    }

    @Override
    public String getProviderId() { return PROVIDER_ID; }

    /** Ejecuta exactamente tres consultas y solo devuelve un ciclo completo. */
    public Optional<OpenCodeUsageResult> readRaw() {
        Instant now = ranges.calculate().fiveHours().to();
        return readRaw(now);
    }

    /** Variante determinista para pruebas; las tres ventanas comparten {@code now}. */
    public Optional<OpenCodeUsageResult> readRaw(Instant now) {
        UsageWindowRanges.Ranges calculated = ranges.calculate(now);
        List<UsageWindowRanges.Range> requested = List.of(calculated.fiveHours(),
                calculated.sevenDays(), calculated.month());
        List<Future<OpenCodeStatsParser.ParseResult>> futures = requested.stream()
                .map(range -> pool.submit(() -> query(range)))
                .toList();
        try {
            OpenCodeStatsParser.ParseResult fiveHours = futures.get(0).get();
            OpenCodeStatsParser.ParseResult sevenDays = futures.get(1).get();
            OpenCodeStatsParser.ParseResult month = futures.get(2).get();
            if (!fiveHours.isValid() || !sevenDays.isValid() || !month.isValid()) {
                return Optional.empty();
            }
            return Optional.of(new OpenCodeUsageResult(now, fiveHours.models(),
                    sevenDays.models(), month.models()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            cancel(futures);
            return Optional.empty();
        } catch (ExecutionException | CancellationException exception) {
            return Optional.empty();
        }
    }

    /**
     * Ejecuta un ciclo completo y transforma el gasto crudo en porcentajes.
     * La transformación solo se realiza después de validar las tres consultas,
     * por lo que nunca puede observarse un resultado parcial.
     */
    public Optional<OpenCodeUsageCalculator.Calculation> readCalculated() {
        return readCalculated(ranges.calculate().fiveHours().to());
    }

    /** Variante determinista del ciclo calculado para pruebas y consumidores. */
    public Optional<OpenCodeUsageCalculator.Calculation> readCalculated(Instant now) {
        return readRaw(now).map(raw -> calculator.calculate(raw.fiveHours(),
                raw.sevenDays(), raw.month()));
    }

    private OpenCodeStatsParser.ParseResult query(UsageWindowRanges.Range range) {
        OpenCodeCliRunner.CliResult result = runner.run(range.fromIso8601(), range.toIso8601());
        return result.isSuccess() ? parser.parse(result.output()) : OpenCodeStatsParser.ParseResult.failure();
    }

    /** Acceso usado por el cierre de la aplicación. */
    public int liveProcessCount() { return runner.liveProcessCount(); }

    public void closePool() { close(); }

    public void shutdown() { close(); }

    public int getLiveProcessCount() { return liveProcessCount(); }

    @Override
    public void close() {
        runner.destroyActiveProcesses();
        pool.shutdownNow();
    }

    @Override
    public Optional<UsageReading> doRead() {
        return readCalculated().map(calculation -> {
            OpenCodeUsageCalculator.ModelUsage total = calculation.total();
            List<UsageWindowReading> windows = List.of(
                    percentage(total, UsageWindowType.FIVE_HOUR),
                    percentage(total, UsageWindowType.SEVEN_DAY),
                    percentage(total, UsageWindowType.MONTHLY));
            return new UsageReading(PROVIDER_ID, Instant.now(), windows);
        });
    }

    private UsageWindowReading percentage(OpenCodeUsageCalculator.ModelUsage total,
                                          UsageWindowType window) {
        return new UsageWindowReading(window, total.percentage(window).intValue(), null);
    }

    /** Parser legado retenido para no romper consumidores durante la migración de v1. */
    public UsageReadResult normalize(String response, Instant queryFinishedAt) {
        try {
            Map<?, ?> root = new ObjectMapper().readValue(response, Map.class);
            Object usage = root.get("usage");
            if (!(usage instanceof Map<?, ?> values)) {
                return UsageReadResult.datos(new UsageReading(PROVIDER_ID, queryFinishedAt, List.of()));
            }
            List<UsageWindowReading> windows = new ArrayList<>();
            addLegacyWindow(values, "rolling", UsageWindowType.ROLLING, queryFinishedAt, windows);
            addLegacyWindow(values, "weekly", UsageWindowType.WEEKLY, queryFinishedAt, windows);
            addLegacyWindow(values, "monthly", UsageWindowType.MONTHLY, queryFinishedAt, windows);
            return UsageReadResult.datos(new UsageReading(PROVIDER_ID, queryFinishedAt, windows));
        } catch (Exception ignored) {
            return UsageReadResult.falloTransitorio(null);
        }
    }

    private void addLegacyWindow(Map<?, ?> usage, String name, UsageWindowType type,
                                 Instant finished, List<UsageWindowReading> target) {
        if (!(usage.get(name) instanceof Map<?, ?> window)) return;
        Object percent = window.get("percent");
        Object reset = window.get("resetsAt");
        try {
            if (percent instanceof Number number && reset instanceof String text) {
                target.add(new UsageWindowReading(type, number.intValue(), Instant.parse(text)));
                return;
            }
            Object relative = window.get("usagePercent");
            Object seconds = window.get("resetInSec");
            if (relative instanceof Number number && seconds instanceof Number delay) {
                target.add(new UsageWindowReading(type, number.intValue(),
                        finished.plusSeconds(delay.longValue())));
            }
        } catch (RuntimeException ignored) { }
    }

    private static void cancel(List<? extends Future<?>> futures) {
        futures.forEach(future -> future.cancel(true));
    }

    private static ExecutorService newPool() {
        ThreadFactory factory = new ThreadFactory() {
            private int number;
            @Override public synchronized Thread newThread(Runnable task) {
                Thread thread = new Thread(task, "opencode-usage-" + ++number);
                thread.setDaemon(true);
                return thread;
            }
        };
        return Executors.newFixedThreadPool(3, factory);
    }
}
