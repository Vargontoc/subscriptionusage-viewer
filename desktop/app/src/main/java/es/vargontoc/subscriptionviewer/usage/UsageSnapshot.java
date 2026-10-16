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
public record UsageSnapshot(Map<String, UsageReading> readings) {

    /**
     * Crea un snapshot vacío.
     */
    public UsageSnapshot() {
        this(Map.of());
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

    /**
     * Devuelve si el snapshot no contiene ningún proveedor.
     *
     * @return {@code true} si no hay lecturas
     */
    public boolean isEmpty() {
        return readings.isEmpty();
    }

    @Override
    public Map<String, UsageReading> readings() {
        return Collections.unmodifiableMap(readings);
    }
}
