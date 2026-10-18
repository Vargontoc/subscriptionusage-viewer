package es.vargontoc.subscriptionviewer.usage;

import es.vargontoc.subscriptionviewer.config.ConfigurationProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Planificador de lecturas periódicas de uso en hilo de fondo.
 *
 * <p>Consulta cada {@link UsageSource} de forma aislada con un tiempo de espera
 * máximo, actualiza el historial con lecturas presentes y publica el resultado
 * por proveedor. Cada fuente tiene su propia cadencia y la siguiente lectura se
 * programa al finalizar la anterior, de modo que una lectura lenta no se solapa.
 </p>
 */
@Component
public class UsagePoller implements SmartLifecycle {

    private static final Duration SOURCE_TIMEOUT = Duration.ofSeconds(5);

    private final ConfigurationProvider configurationProvider;
    private final List<UsageSource> sources;
    private final UsageHistory usageHistory;
    private final UsageSnapshotPublisher publisher;
    private final Clock clock;
    private final ScheduledExecutorService executor;
    // Las lecturas no pueden ir al executor de planificación: runCycle ya ocupa su
    // único hilo y la lectura quedaría en cola hasta agotar el timeout.
    private final ExecutorService readExecutor = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "usage-reader");
        thread.setDaemon(true);
        return thread;
    });

    private volatile boolean running = false;
    private final Map<String, ScheduledFuture<?>> scheduled = new ConcurrentHashMap<>();

    /**
     * Constructor para producción.
     */
    @Autowired
    public UsagePoller(ConfigurationProvider configurationProvider,
                       List<UsageSource> sources,
                       UsageHistory usageHistory,
                       UsageSnapshotPublisher publisher,
                       Clock clock) {
        this(configurationProvider, sources, usageHistory, publisher, clock,
                Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread thread = new Thread(r, "usage-poller");
                    thread.setDaemon(true);
                    return thread;
                }));
    }

    /**
     * Constructor con executor configurable (usado principalmente en tests).
     */
    UsagePoller(ConfigurationProvider configurationProvider,
                List<UsageSource> sources,
                UsageHistory usageHistory,
                UsageSnapshotPublisher publisher,
                Clock clock,
                ScheduledExecutorService executor) {
        this.configurationProvider = configurationProvider;
        this.sources = List.copyOf(sources);
        this.usageHistory = usageHistory;
        this.publisher = publisher;
        this.clock = clock;
        this.executor = executor;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;
        for (UsageSource source : sources) {
            // La gestión de autenticación de v1 se conserva hasta TASK-021;
            // esta tarea solo elimina su backoff y pausa tras los fallos.
            if (isOpenCode(source) && !configurationProvider.hasOpenCodeApiKey()) {
                publisher.publish(source.getProviderId(), UsageReadResult.claveAusente(), clock.instant());
                continue;
            }
            // El primer ciclo se lanza al arrancar. Los siguientes se programan
            // desde poll(), después de que termine la lectura de esta fuente.
            scheduleNext(source, Duration.ZERO);
        }
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        scheduled.values().forEach(future -> future.cancel(false));
        executor.shutdown();
        readExecutor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    void runCycle() {
        for (UsageSource source : sources) {
            UsageReadResult result = readWithTimeout(source);
            Optional<UsageReading> reading = result.datos();
            reading.ifPresent(usageHistory::add);
            publisher.publish(source.getProviderId(), reading);
        }
    }

    private Duration intervalFor(UsageSource source) {
        return isOpenCode(source)
                ? configurationProvider.getOpenCodePollInterval()
                : configurationProvider.getUsagePollInterval();
    }

    private boolean isOpenCode(UsageSource source) {
        return "opencode".equalsIgnoreCase(source.getProviderId())
                || source.getProviderId().toLowerCase().contains("opencode");
    }

    private void scheduleNext(UsageSource source, Duration delay) {
        if (!running) return;
        ScheduledFuture<?> future = executor.schedule(() -> poll(source),
                Math.max(0, delay.toMillis()), TimeUnit.MILLISECONDS);
        if (future != null) {
            scheduled.put(source.getProviderId(), future);
        }
    }

    private void poll(UsageSource source) {
        if (!running) return;
        readExecutor.submit(() -> {
            UsageReadResult result = readWithTimeout(source);
            Instant completedAt = clock.instant();
            result.datos().ifPresent(usageHistory::add);
            publisher.publish(source.getProviderId(), result, completedAt);
            // El retardo se cuenta desde el fin de la lectura. Así una fuente
            // lenta no se solapa consigo misma y un fallo se reintenta con la
            // misma cadencia, sin backoff ni pausa.
            scheduleNext(source, intervalFor(source));
        });
    }

    private UsageReadResult readWithTimeout(UsageSource source) {
        Future<UsageReadResult> future = readExecutor.submit(source::read);
        try {
            return future.get(SOURCE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return UsageReadResult.falloTransitorio(e);
        } catch (ExecutionException | TimeoutException e) {
            future.cancel(true);
            return UsageReadResult.falloTransitorio(e);
        }
    }
}
