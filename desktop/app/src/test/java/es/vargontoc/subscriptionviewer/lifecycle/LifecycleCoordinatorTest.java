package es.vargontoc.subscriptionviewer.lifecycle;

import es.vargontoc.subscriptionviewer.context.ApplicationContextConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import es.vargontoc.subscriptionviewer.usage.UsagePoller;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Pruebas del coordinador de ciclo de vida.
 *
 * <p>Verifican que el coordinador mantiene el proceso bloqueado hasta recibir
 * la señal de cierre, que permite cerrar el contexto de forma ordenada y que no
 * se produce un interbloqueo si la señal llega durante la inicialización.</p>
 */
class LifecycleCoordinatorTest {

    @Test
    void requestShutdownFromAnotherThreadReleasesWaitForShutdown() throws Exception {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(ApplicationContextConfig.class)
                .web(WebApplicationType.NONE)
                .run()) {

            LifecycleCoordinator coordinator = context.getBean(LifecycleCoordinator.class);

            Thread shutdownThread = new Thread(() -> {
                try {
                    Thread.sleep(100L);
                    coordinator.requestShutdown();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            shutdownThread.start();

            coordinator.waitForShutdown();

            assertThat(context.isActive())
                    .as("El contexto sigue activo tras retornar waitForShutdown()")
                    .isTrue();

            context.close();

            assertThat(context.isActive())
                    .as("El contexto debe quedar cerrado de forma ordenada")
                    .isFalse();

            shutdownThread.join();
        }
    }

    @Test
    void stopShutsDownUsagePollerBeforeReleasingLatch() {
        UsagePoller poller = mock(UsagePoller.class);
        LifecycleCoordinator coordinator = new LifecycleCoordinator(poller);

        coordinator.stop();

        verify(poller).stop();
        assertThat(coordinator.isRunning()).isFalse();
    }

    @Test
    void requestShutdownDoesNotStopPollerButReleasesLatch() throws InterruptedException {
        UsagePoller poller = mock(UsagePoller.class);
        LifecycleCoordinator coordinator = new LifecycleCoordinator(poller);

        coordinator.requestShutdown();

        verify(poller, times(0)).stop();
        assertThat(coordinator.waitForShutdown(100, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void shutdownRequestedDuringStartupDoesNotDeadlockAndExitsWithNonZeroCode() throws Exception {
        String javaBinary = Path.of(System.getProperty("java.home"), "bin", "java")
                .toAbsolutePath()
                .toString();
        String classpath = System.getProperty("java.class.path");

        ProcessBuilder builder = new ProcessBuilder(
                javaBinary,
                "-cp", classpath,
                "es.vargontoc.subscriptionviewer.launcher.ApplicationLauncher");
        builder.inheritIO();

        Process process = builder.start();

        // Dar tiempo a que el proceso comience la inicialización del contexto.
        Thread.sleep(500L);

        // Simular una solicitud de cierre externa durante el arranque.
        process.destroy();

        boolean finished = process.waitFor(5, TimeUnit.SECONDS);

        assertThat(finished)
                .as("El proceso debe terminar sin interbloqueo en menos de 5 segundos")
                .isTrue();
        assertThat(process.exitValue())
                .as("Al recibir una señal de cierre externa el código de salida debe ser distinto de cero")
                .isNotEqualTo(0);
    }
}
