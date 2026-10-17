package es.vargontoc.subscriptionviewer.config;

import java.util.Objects;

/**
 * API key de OpenCode encapsulada para evitar que se filtre al representar la
 * configuración o al escribirla accidentalmente en un log.
 */
public final class OpenCodeApiKey {

    private static final String REDACTED = "***";

    private final String value;

    /**
     * Crea una clave. Un valor {@code null} representa que no hay clave.
     */
    public OpenCodeApiKey(String value) {
        this.value = value;
    }

    /**
     * Devuelve el valor para el único punto que construye la autenticación HTTP.
     */
    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return REDACTED;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OpenCodeApiKey that)) {
            return false;
        }
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }
}
