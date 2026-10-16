package es.vargontoc.subscriptionviewer.usage;

import java.time.Duration;

/**
 * Resultado de evaluar la vigencia de una ventana de uso.
 *
 * @param remainingTime tiempo restante no negativo hasta el reinicio
 * @param valid         {@code true} si la ventana aún no ha caducado
 */
public record CountdownResult(Duration remainingTime, boolean valid) {
}
