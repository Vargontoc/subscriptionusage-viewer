package es.vargontoc.subscriptionviewer.usage;

import java.time.Instant;

/**
 * Lectura de una ventana de límite de consumo.
 *
 * @param type            tipo de ventana (sesión o semanal)
 * @param usedPercentage  porcentaje consumido (0-100)
 * @param resetsAt        instante en el que se reinicia la ventana
 */
public record UsageWindowReading(UsageWindowType type, int usedPercentage, Instant resetsAt) {
}
