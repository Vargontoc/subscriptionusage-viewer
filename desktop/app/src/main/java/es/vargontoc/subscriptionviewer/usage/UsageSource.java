package es.vargontoc.subscriptionviewer.usage;

import java.util.Optional;

/**
 * Contrato de una fuente de lectura de uso de un proveedor de AI.
 *
 * <p>Cada implementación representa un proveedor distinto (Claude, OpenAI, etc.).
 * El método {@link #read()} encapsula cualquier fallo interno y lo convierte en
 * ausencia de datos, de modo que el consumidor no necesita conocer el origen
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
     * <p>Las implementaciones deben sobreescribir {@link #doRead()}.
     * Este método envuelve la llamada para que cualquier excepción se
     * traduzca en {@link Optional#empty()}.</p>
     *
     * @return lectura de uso o {@link Optional#empty()} si no hay datos o falla
     */
    default Optional<UsageReading> read() {
        try {
            return doRead();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Implementación concreta de la lectura.
     *
     * @return lectura de uso o {@link Optional#empty()} si no hay datos
     * @throws Exception si ocurre un error no recuperable durante la lectura
     */
    Optional<UsageReading> doRead() throws Exception;
}
