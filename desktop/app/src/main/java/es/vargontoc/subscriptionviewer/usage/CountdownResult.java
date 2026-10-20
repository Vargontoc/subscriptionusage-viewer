package es.vargontoc.subscriptionviewer.usage;

import java.time.Duration;

/**
 * Resultado de evaluar la vigencia de una ventana de uso.
 *
 * @param remainingTime tiempo restante no negativo hasta el reinicio
 * @param valid         {@code true} si la ventana aún no ha caducado
 * @param hasKnownReset {@code true} si existe un instante de reinicio conocido
 */
public record CountdownResult(Duration remainingTime, boolean valid, boolean hasKnownReset) {

    /**
     * Constructor compatible con el resultado original, que asumía un reinicio conocido.
     */
    public CountdownResult(Duration remainingTime, boolean valid) {
        this(remainingTime, valid, true);
    }

    /**
     * Devuelve una representación breve para la presentación de la cuenta atrás.
     * Las ventanas de al menos un día se expresan en días; las cortas conservan
     * el formato de horas y minutos usado por Claude.
     *
     * @return tiempo restante formateado en español
     */
    public String formattedRemainingTime() {
        if (!valid || !hasKnownReset) {
            return "";
        }
        long days = remainingTime.toDays();
        if (days > 0) {
            return days + (days == 1 ? " día" : " días");
        }
        long hours = remainingTime.toHours();
        long minutes = remainingTime.toMinutesPart();
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        long seconds = remainingTime.toSecondsPart();
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }
}
