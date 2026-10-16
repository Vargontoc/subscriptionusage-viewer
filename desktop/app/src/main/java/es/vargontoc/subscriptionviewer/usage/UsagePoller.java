package es.vargontoc.subscriptionviewer.usage;

import es.vargontoc.subscriptionviewer.config.ConfigurationProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
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
 * por proveedor. Usa un único ejecutor programado con retardo fijo contado desde
 * el fin de cada ciclo, de modo que una lectura lenta retrasa la siguiente pero
 * no la solapa.</p>
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
        Duration interval = configurationProvider.getUsagePollInterval();
        executor.scheduleWithFixedDelay(this::runCycle, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
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
            Optional<UsageReading> reading = readWithTimeout(source);
            reading.ifPresent(usageHistory::add);
            publisher.publish(source.getProviderId(), reading);
        }
    }

    private Optional<UsageReading> readWithTimeout(UsageSource source) {
        Future<Optional<UsageReading>> future = readExecutor.submit(source::read);
        try {
            return future.get(SOURCE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return Optional.empty();
        } catch (ExecutionException | TimeoutException e) {
            future.cancel(true);
            return Optional.empty();
        }
    }
}
