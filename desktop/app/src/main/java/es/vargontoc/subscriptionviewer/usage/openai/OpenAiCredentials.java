package es.vargontoc.subscriptionviewer.usage.openai;

import java.util.Objects;

/**
 * Credenciales de OpenAI mantenidas como un valor opaco.
 *
 * <p>Los valores solo son accesibles desde el paquete de OpenAI, en el punto
 * que construye la petición autenticada. En particular, no se ofrecen
 * getters públicos ni una representación que pueda acabar en un log.</p>
 */
public final class OpenAiCredentials {

    private final String accessToken;
    private final String accountId;

    public OpenAiCredentials(String accessToken, String accountId) {
        this.accessToken = Objects.requireNonNull(accessToken, "accessToken");
        this.accountId = Objects.requireNonNull(accountId, "accountId");
    }

    /** Acceso interno exclusivo para construir la cabecera Authorization. */
    String accessTokenForHeader() {
        return accessToken;
    }

    /** Acceso interno exclusivo para construir la cabecera de cuenta. */
    String accountIdForHeader() {
        return accountId;
    }

    /**
     * No devuelve una máscara con letras que puedan coincidir con un secreto.
     * Una representación vacía es deliberadamente fija y no contiene ningún
     * carácter del valor protegido.
     */
    @Override
    public String toString() {
        return "";
    }
}
