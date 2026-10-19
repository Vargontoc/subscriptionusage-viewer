package es.vargontoc.subscriptionviewer.usage;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Estado inmutable publicado para un proveedor, sin credenciales. */
public record UsageProviderState(Estado estado, UsageReading reading, Instant lastSuccessfulQuery) {
    public enum Estado {
        CORRECTO, DESACTUALIZADO, SIN_DATOS, SESION_CADUCADA
    }

    public UsageProviderState {
        Objects.requireNonNull(estado, "estado");
        if (estado == Estado.CORRECTO && reading == null) {
            throw new IllegalArgumentException("El estado correcto requiere datos");
        }
    }

    public Optional<UsageReading> readingOptional() { return Optional.ofNullable(reading); }
    public Optional<Instant> lastSuccessfulQueryOptional() { return Optional.ofNullable(lastSuccessfulQuery); }
    public boolean isStale() { return estado == Estado.DESACTUALIZADO; }
}
