package es.vargontoc.subscriptionviewer.usage;

import org.springframework.stereotype.Component;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

/**
 * Publicador del estado de uso por proveedor.
 *
 * <p>Mantiene estado independiente por proveedor y notifica en el EDT.</p>
 */
@Component
public class UsageSnapshotPublisher {

    private final Map<String, UsageReading> latestReadings = new ConcurrentHashMap<>();
    private final Map<String, UsageProviderState> providerStates = new ConcurrentHashMap<>();
    private final List<UsageSnapshotListener> listeners = new CopyOnWriteArrayList<>();
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
        // OpenCode tiene un ciclo atómico: la ausencia de datos no es un nuevo
        // estado, sino una consulta descartada. Conservamos la última lectura
        // válida (y evitamos despertar a la vista).
        if (isOpenCode(providerId) && reading.isEmpty()) {
            return;
        }
        reading.ifPresentOrElse(
                value -> latestReadings.put(providerId, value),
                () -> latestReadings.remove(providerId)
        );
        notifyListeners();
    }

    /** Publica un resultado clasificado, conservando el último dato válido cuando procede. */
    public void publish(String providerId, UsageReadResult result, Instant queryTime) {
        if (providerId == null || result == null || queryTime == null) {
            throw new NullPointerException("providerId, result y queryTime son obligatorios");
        }
        // Para OpenCode solo existen datos o ausencia. Un fallo no debe crear
        // estados de error/desactualización, ni borrar el último ciclo bueno.
        if (isOpenCode(providerId) && result.estado() != UsageReadResult.Estado.DATOS) {
            return;
        }
        UsageProviderState previous = providerStates.get(providerId);
        UsageProviderState next;
        switch (result.estado()) {
            case DATOS -> {
                UsageReading reading = result.datos().orElseThrow();
                latestReadings.put(providerId, reading);
                next = new UsageProviderState(UsageProviderState.Estado.CORRECTO, reading, queryTime);
            }
            case FALLO_TRANSITORIO -> next = stateAfterFailure(previous,
                    UsageProviderState.Estado.DESACTUALIZADO);
            default -> throw new IllegalStateException("Estado no soportado: " + result.estado());
        }
        providerStates.put(providerId, next);
        notifyListeners();
    }

    public void publish(String providerId, UsageReadResult result) {
        publish(providerId, result, Instant.now());
    }

    private UsageProviderState stateAfterFailure(UsageProviderState previous,
                                                  UsageProviderState.Estado estado) {
        UsageReading reading = previous == null ? null : previous.reading();
        Instant successful = previous == null ? null : previous.lastSuccessfulQuery();
        if (reading == null) {
            return new UsageProviderState(estado == UsageProviderState.Estado.DESACTUALIZADO
                    ? UsageProviderState.Estado.SIN_DATOS : estado, null, null);
        }
        return new UsageProviderState(estado, reading, successful);
    }

    private boolean isOpenCode(String providerId) {
        return providerId != null && providerId.toLowerCase(java.util.Locale.ROOT).contains("opencode");
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
        return new UsageSnapshot(Map.copyOf(latestReadings), Map.copyOf(providerStates));
    }

    private void notifyListeners() {
        UsageSnapshot snapshot = getCurrentSnapshot();
        for (UsageSnapshotListener listener : List.copyOf(listeners)) {
            dispatcher.execute(() -> listener.onSnapshotChanged(snapshot));
        }
    }
}
