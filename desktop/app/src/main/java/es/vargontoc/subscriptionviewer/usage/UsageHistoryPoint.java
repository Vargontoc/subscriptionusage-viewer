package es.vargontoc.subscriptionviewer.usage;

import java.time.Instant;

/**
 * Punto de la serie temporal de evolución de uso de una ventana.
 *
 * @param usedPercentage porcentaje consumido en el instante de registro
 * @param resetsAt       instante en que se reinicia la ventana, si la fuente lo conoce
 * @param writtenAt      instante en que la fuente registró la lectura
 */
public record UsageHistoryPoint(int usedPercentage, Instant resetsAt, Instant writtenAt) {

    /**
     * Constructor compatible para puntos de fuentes que no proporcionan el reinicio.
     */
    public UsageHistoryPoint(int usedPercentage, Instant writtenAt) {
        this(usedPercentage, null, writtenAt);
    }
}
