package es.vargontoc.subscriptionviewer.usage;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Evalúa la vigencia y el tiempo restante de una ventana de uso.
 *
 * <p>Usa un {@link Clock} inyectado para poder simular el paso del tiempo en
 * pruebas. El cálculo es estricto frente a {@code resets_at}: si el instante de
 * reinicio ya ha pasado, la ventana no es vigente y el tiempo restante es cero.</p>
 */
@Component
public class CountdownEvaluator {

    private final Clock clock;

    public CountdownEvaluator(Clock clock) {
        this.clock = clock;
    }

    /**
     * Evalúa una ventana frente a la hora actual.
     *
     * @param resetsAt instante en el que se reinicia la ventana
     * @return resultado con tiempo restante no negativo y vigencia
     */
    public CountdownResult evaluate(Instant resetsAt) {
        return evaluate(null, resetsAt);
    }

    /**
     * Evalúa una ventana teniendo en cuenta el proveedor que la presenta.
     * La vigencia real se aplica también a OpenAI. Gemini conserva su
     * semántica especial porque sus ventanas se muestran con cuenta atrás
     * cero una vez vencidas.
     *
     * @param providerId identificador del proveedor
     * @param resetsAt instante en el que se reinicia la ventana
     * @return resultado con tiempo restante y vigencia de presentación
     */
    public CountdownResult evaluate(String providerId, Instant resetsAt) {
        Instant now = clock.instant();
        if (resetsAt == null) {
            if ("gemini".equalsIgnoreCase(providerId)) {
                return new CountdownResult(Duration.ZERO, true, false);
            }
            return new CountdownResult(Duration.ZERO, false, false);
        }
        if (resetsAt.isAfter(now)) {
            return new CountdownResult(Duration.between(now, resetsAt), true, true);
        }
        if ("gemini".equalsIgnoreCase(providerId)) {
            return new CountdownResult(Duration.ZERO, true, true);
        }
        return new CountdownResult(Duration.ZERO, false, true);
    }
}
