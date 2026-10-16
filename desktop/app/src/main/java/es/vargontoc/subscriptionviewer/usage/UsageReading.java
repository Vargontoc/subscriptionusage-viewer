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
public record UsageReading(String providerId, Instant writtenAt, List<UsageWindowReading> windows) {
}
