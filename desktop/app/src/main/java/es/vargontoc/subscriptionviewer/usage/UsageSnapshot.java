package es.vargontoc.subscriptionviewer.usage;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Snapshot inmutable del estado publicado de uso por proveedor.
 *
 * @param readings lecturas indexadas por identificador de proveedor
 */
public record UsageSnapshot(Map<String, UsageReading> readings,
                            Map<String, UsageProviderState> providerStates) {

    public UsageSnapshot(Map<String, UsageReading> readings) {
        this(readings, statesFrom(readings));
    }

    /**
     * Crea un snapshot vacío.
     */
    public UsageSnapshot() {
        this(Map.of(), Map.of());
    }

    public UsageSnapshot {
        readings = Map.copyOf(readings);
        providerStates = Map.copyOf(providerStates);
    }

    private static Map<String, UsageProviderState> statesFrom(Map<String, UsageReading> readings) {
        Map<String, UsageProviderState> states = new HashMap<>();
        readings.forEach((provider, reading) -> states.put(provider,
                new UsageProviderState(UsageProviderState.Estado.CORRECTO, reading, reading.writtenAt())));
        return states;
    }

    /**
     * Devuelve la lectura de un proveedor si está presente.
     *
     * @param providerId identificador del proveedor
     * @return lectura del proveedor
     */
    public Optional<UsageReading> readingOf(String providerId) {
        return Optional.ofNullable(readings.get(providerId));
    }

    public Optional<UsageProviderState> stateOf(String providerId) {
        return Optional.ofNullable(providerStates.get(providerId));
    }

    /** Alias explícito para consumidores de la vista. */
    public Optional<UsageProviderState> providerStateOf(String providerId) {
        return stateOf(providerId);
    }

    /**
     * Devuelve si el snapshot no contiene ningún proveedor.
     *
     * @return {@code true} si no hay lecturas
     */
    public boolean isEmpty() {
        return readings.isEmpty() && providerStates.isEmpty();
    }

    @Override
    public Map<String, UsageReading> readings() {
        return Collections.unmodifiableMap(readings);
    }

    @Override
    public Map<String, UsageProviderState> providerStates() {
        return Collections.unmodifiableMap(providerStates);
    }
}
