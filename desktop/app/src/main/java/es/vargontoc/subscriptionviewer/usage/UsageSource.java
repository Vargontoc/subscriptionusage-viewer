package es.vargontoc.subscriptionviewer.usage;

import java.util.Optional;

/**
 * Contrato de una fuente de lectura de uso de un proveedor de AI.
 *
 * <p>Cada implementación representa un proveedor distinto (Claude, OpenAI, etc.).
 * El método {@link #read()} encapsula cualquier fallo interno y lo convierte en
 * un resultado clasificado, de modo que el consumidor no necesita conocer el origen
 * ni manejar excepciones de una fuente concreta.</p>
 */
public interface UsageSource {

    /**
     * Identificador único del proveedor que representa esta fuente.
     *
     * @return identificador neutro del proveedor
     */
    String getProviderId();

    /**
     * Realiza la lectura del uso para el proveedor.
     *
     * <p>Las implementaciones existentes pueden sobreescribir {@link #doRead()}.
     * Las fuentes que necesiten devolver un estado distinto de datos o fallo
     * transitorio pueden sobreescribir {@link #doReadResult()}.
     * Este método envuelve la llamada para que cualquier excepción se
     * traduzca en {@link UsageReadResult.Estado#FALLO_TRANSITORIO}.</p>
     *
     * @return resultado clasificado de la lectura
     */
    default UsageReadResult read() {
        try {
            return doReadResult();
        } catch (Exception e) {
            return UsageReadResult.falloTransitorio(e);
        }
    }

    /**
     * Realiza la lectura clasificada. Su implementación por defecto conserva
     * la compatibilidad con las fuentes basadas en {@link #doRead()}.
     */
    default UsageReadResult doReadResult() throws Exception {
        return doRead().map(UsageReadResult::datos)
                .orElseGet(() -> UsageReadResult.falloTransitorio(null));
    }

    /**
     * Implementación concreta de la lectura.
     *
     * @return lectura de uso o {@link Optional#empty()} si no hay datos
     * @throws Exception si ocurre un error no recuperable durante la lectura
     */
    Optional<UsageReading> doRead() throws Exception;
}
