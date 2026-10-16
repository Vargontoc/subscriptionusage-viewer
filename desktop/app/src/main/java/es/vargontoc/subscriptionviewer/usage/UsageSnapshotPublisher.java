package es.vargontoc.subscriptionviewer.usage;

import org.springframework.stereotype.Component;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Publicador del estado de uso por proveedor.
 *
 * <p>Mantiene el último estado conocido de cada proveedor, elimina al proveedor
 * cuando una lectura posterior devuelve ausencia y notifica a los listeners en
 * el hilo de interfaz (EDT). Los snapshots entregados son inmutables.</p>
 */
@Component
public class UsageSnapshotPublisher {

    private final Map<String, UsageReading> latestReadings = new ConcurrentHashMap<>();
    private final List<UsageSnapshotListener> listeners = new ArrayList<>();
    private final Executor dispatcher;

    /**
     * Constructor para producción que despacha al EDT con {@link SwingUtilities#invokeLater}.
     */
    public UsageSnapshotPublisher() {
        this(SwingUtilities::invokeLater);
    }

    /**
     * Constructor con dispatcher configurable (utilizado principalmente en tests).
     *
     * @param dispatcher ejecutor donde se notifican los listeners
     */
    UsageSnapshotPublisher(Executor dispatcher) {
        this.dispatcher = dispatcher;
    }

    /**
     * Publica el resultado de una lectura para un proveedor.
     *
     * @param providerId identificador del proveedor
     * @param reading    lectura obtenida o {@link Optional#empty()} si no hay datos
     */
    public void publish(String providerId, Optional<UsageReading> reading) {
        reading.ifPresentOrElse(
                value -> latestReadings.put(providerId, value),
                () -> latestReadings.remove(providerId)
        );
        notifyListeners();
    }

    /**
     * Añade un listener que recibirá notificaciones en el dispatcher configurado.
     *
     * @param listener listener a registrar
     */
    public void addListener(UsageSnapshotListener listener) {
        listeners.add(listener);
    }

    /**
     * Devuelve el snapshot actual sin notificar.
     *
     * @return snapshot inmutable con el último estado por proveedor
     */
    public UsageSnapshot getCurrentSnapshot() {
        return new UsageSnapshot(Map.copyOf(latestReadings));
    }

    private void notifyListeners() {
        UsageSnapshot snapshot = getCurrentSnapshot();
        for (UsageSnapshotListener listener : List.copyOf(listeners)) {
            dispatcher.execute(() -> listener.onSnapshotChanged(snapshot));
        }
    }
}
