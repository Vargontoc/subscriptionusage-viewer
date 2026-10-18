package es.vargontoc.subscriptionviewer.launcher;

import es.vargontoc.subscriptionviewer.config.AppProperties;
import es.vargontoc.subscriptionviewer.config.ConfigurationProvider;
import es.vargontoc.subscriptionviewer.usage.CountdownEvaluator;
import es.vargontoc.subscriptionviewer.usage.UsageHistory;
import es.vargontoc.subscriptionviewer.usage.UsageReadResult;
import es.vargontoc.subscriptionviewer.usage.UsageSnapshotPublisher;
import es.vargontoc.subscriptionviewer.ui.UsageWindow;
import es.vargontoc.subscriptionviewer.usage.opencode.OpenCodeUsageSource;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

import javax.swing.SwingUtilities;
import java.time.Clock;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifica que una consulta lenta permanece fuera del hilo de interfaz. */
class OpenCodeResourceAndResponsivenessIntegrationTest {

    @Test
    @Disabled("v1 - retirar en TASK-021")
    void userInterfaceRemainsResponsiveWhileOpenCodeTakesSixSeconds() throws Exception {
        try (SlowOpenCodeServer server = new SlowOpenCodeServer(6_000)) {
            MockEnvironment environment = new MockEnvironment()
                    .withProperty("subscription-viewer.opencode.api-key", "test-key")
                    .withProperty("subscription-viewer.opencode.usage-url", server.url());
            ConfigurationProvider configuration = new ConfigurationProvider(new AppProperties(), environment);
            OpenCodeUsageSource source = new OpenCodeUsageSource(new ObjectMapper(), configuration);
            UsageSnapshotPublisher publisher = new UsageSnapshotPublisher();
            UsageWindow window = new UsageWindow(publisher, new CountdownEvaluator(Clock.systemUTC()),
                    new UsageHistory());
            window.subscribe();

            CompletableFuture<UsageReadResult> pendingRead = CompletableFuture.supplyAsync(source::read);
            awaitRequest(server);

            long started = System.nanoTime();
            CompletableFuture<Void> uiAction = CompletableFuture.runAsync(() -> {
                try {
                    SwingUtilities.invokeAndWait(
                            () -> window.onSnapshotChanged(publisher.getCurrentSnapshot()));
                } catch (Exception exception) {
                    throw new IllegalStateException("No se pudo ejecutar la acción de interfaz", exception);
                }
            });
            uiAction.get(1, TimeUnit.SECONDS);
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

            assertThat(elapsedMillis).isLessThan(1_000);
            assertThat(pendingRead).isNotDone();
            pendingRead.get(8, TimeUnit.SECONDS);
        }
    }

    private static void awaitRequest(SlowOpenCodeServer server) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (server.requests() == 0 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(server.requests()).as("el servidor lento debe recibir la consulta").isPositive();
    }
}
