package es.vargontoc.subscriptionviewer.usage;

import java.time.Instant;
import java.util.List;

/**
 * Lectura de uso de un proveedor en un instante dado.
 *
 * @param providerId identificador neutro del proveedor (p. ej. "claude")
 * @param writtenAt  instante en el que la fuente registró la lectura
 * @param windows    lecturas de cada ventana disponible
 */
public record UsageReading(String providerId, Instant writtenAt, List<UsageWindowReading> windows,
                           String seriesId, List<UsageReading> seriesReadings) {
    /** Constructor compatible con las lecturas de Claude, que tienen una única serie. */
    public UsageReading(String providerId, Instant writtenAt, List<UsageWindowReading> windows) {
        this(providerId, writtenAt, windows, null, List.of());
    }

    /** Forma alternativa útil para construir lecturas de una serie de OpenCode. */
    public UsageReading(String providerId, String seriesId, Instant writtenAt,
                        List<UsageWindowReading> windows) {
        this(providerId, writtenAt, windows, seriesId, List.of());
    }

    /** Compatibilidad con el constructor de cuatro componentes anterior. */
    public UsageReading(String providerId, Instant writtenAt, List<UsageWindowReading> windows,
                        String seriesId) {
        this(providerId, writtenAt, windows, seriesId, List.of());
    }

    /**
     * Lectura compuesta usada por OpenCode: contiene una fila por modelo y la
     * fila agregada Total. Las lecturas normales no tienen filas hijas.
     */
    public UsageReading(String providerId, Instant writtenAt, List<UsageWindowReading> windows,
                        List<UsageReading> seriesReadings) {
        this(providerId, writtenAt, windows, null, seriesReadings);
    }

    public UsageReading {
        windows = List.copyOf(windows == null ? List.of() : windows);
        seriesReadings = List.copyOf(seriesReadings == null ? List.of() : seriesReadings);
    }
}
