package es.vargontoc.subscriptionviewer.lifecycle;

import es.vargontoc.subscriptionviewer.usage.UsagePoller;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Coordinador del ciclo de vida de la aplicación de escritorio.
 *
 * <p>Mantiene el proceso vivo bloqueando el hilo principal mediante un
 * {@link CountDownLatch} hasta que se recibe una señal de cierre. La señal puede
 * provenir del menú de bandeja ({@link #requestShutdown()}) o del propio
 * contenedor de Spring cuando éste se detiene (vía {@link SmartLifecycle}).</p>
 *
 * <p>Implementar {@link SmartLifecycle} permite que Spring invoque
 * {@link #stop()} durante el cierre del contexto, liberando cualquier hilo
 * bloqueado en {@link #waitForShutdown()} y evitando interbloqueos.</p>
 */
@Component
public class LifecycleCoordinator implements SmartLifecycle {

    private final CountDownLatch shutdownLatch = new CountDownLatch(1);
    private final UsagePoller usagePoller;
    private volatile boolean running = false;

    public LifecycleCoordinator(UsagePoller usagePoller) {
        this.usagePoller = usagePoller;
    }

    /**
     * Bloquea el hilo invocador hasta que se solicite el cierre de la aplicación.
     *
     * @throws InterruptedException si el hilo es interrumpido mientras espera
     */
    public void waitForShutdown() throws InterruptedException {
        shutdownLatch.await();
    }

    /**
     * Bloquea el hilo invocador hasta que se solicite el cierre o transcurra el
     * tiempo indicado.
     *
     * @param timeout tiempo máximo de espera
     * @param unit    unidad de tiempo
     * @return {@code true} si se recibió la señal de cierre, {@code false} si
     *         expiró el tiempo
     * @throws InterruptedException si el hilo es interrumpido mientras espera
     */
    public boolean waitForShutdown(long timeout, TimeUnit unit) throws InterruptedException {
        return shutdownLatch.await(timeout, unit);
    }

    /**
     * Solicita el cierre de la aplicación liberando el latch de apagado.
     *
     * <p>Es seguro invocar este método varias veces: tras la primera llamada las
     * siguientes no tienen efecto.</p>
     */
    public void requestShutdown() {
        shutdownLatch.countDown();
    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        usagePoller.stop();
        requestShutdown();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
