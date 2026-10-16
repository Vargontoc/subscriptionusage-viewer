package es.vargontoc.subscriptionviewer.usage;

import java.time.Instant;

/**
 * Punto de la serie temporal de evolución de uso de una ventana.
 *
 * @param usedPercentage porcentaje consumido en el instante de registro
 * @param writtenAt      instante en que la fuente registró la lectura
 */
public record UsageHistoryPoint(int usedPercentage, Instant writtenAt) {
}
