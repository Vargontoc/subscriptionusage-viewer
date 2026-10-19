package es.vargontoc.subscriptionviewer.usage;

import java.util.Objects;
import java.util.Optional;

/** Resultado clasificado de una lectura de uso. */
public final class UsageReadResult {
    public enum Estado { DATOS, FALLO_TRANSITORIO, SESION_CADUCADA }
    private final Estado estado;
    private final UsageReading datos;
    private final Throwable causa;

    private UsageReadResult(Estado estado, UsageReading datos, Throwable causa) {
        this.estado = Objects.requireNonNull(estado, "estado");
        this.datos = datos;
        this.causa = causa;
        if ((estado == Estado.DATOS) != (datos != null)) throw new IllegalArgumentException("Datos incompatibles con el estado");
    }
    public static UsageReadResult datos(UsageReading datos) { return new UsageReadResult(Estado.DATOS, Objects.requireNonNull(datos), null); }
    public static UsageReadResult falloTransitorio(Throwable causa) { return new UsageReadResult(Estado.FALLO_TRANSITORIO, null, causa); }
    /** Resultado usado cuando el proveedor rechaza las credenciales por sesión caducada. */
    public static UsageReadResult sesionCaducada() { return new UsageReadResult(Estado.SESION_CADUCADA, null, null); }
    public Estado estado() { return estado; }
    public Optional<UsageReading> datos() { return Optional.ofNullable(datos); }
    public Optional<Throwable> causa() { return Optional.ofNullable(causa); }
    /** Accesos de conveniencia para migrar consumidores antiguos. */
    public boolean isPresent() { return datos != null; }
    public boolean isEmpty() { return datos == null; }
    public UsageReading get() { return Objects.requireNonNull(datos, "No hay datos"); }
    public void ifPresent(java.util.function.Consumer<UsageReading> consumer) { if (datos != null) consumer.accept(datos); }
}
