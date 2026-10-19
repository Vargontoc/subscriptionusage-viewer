package es.vargontoc.subscriptionviewer.usage;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Historial en memoria de la evolución de uso por proveedor, serie y ventana.
 *
 * <p>Claude conserva la deduplicación cronológica de v1. Las series de OpenCode
 * y OpenAI se deduplican únicamente por porcentaje: un cambio de la ventana no
 * constituye por sí mismo un nuevo punto. Cada serie tiene un tamaño máximo documentado;
 * al superarlo se descartan los puntos más antiguos. El historial no persiste
 * ni se reutiliza entre instancias.</p>
 */
@Component
public class UsageHistory {

    /**
     * Número máximo de puntos que conserva cada serie temporal.
     */
    public static final int MAX_POINTS_PER_SERIES = 100;
    public static final String TOTAL_SERIES = "Total";

    private final Map<Key, List<UsageHistoryPoint>> series = new HashMap<>();

    /**
     * Añade los puntos de una lectura al historial.
     *
     * <p>La deduplicación depende del proveedor. Las lecturas de OpenCode se
     * comparan por porcentaje e instante de consulta, las de OpenAI solo por
     * porcentaje y las de Claude conservan la regla cronológica.</p>
     *
     * @param reading lectura de uso a incorporar
     */
    public synchronized void add(UsageReading reading) {
        Objects.requireNonNull(reading, "La lectura no puede ser nula");
        // Las lecturas de OpenCode que representan el agregado no llevan un
        // identificador de modelo. En el historial deben vivir en la serie
        // explícita Total, no en la clave heredada de v1 (seriesId == null).
        String seriesId = reading.seriesId();
        if (isOpenCode(reading.providerId()) && seriesId == null) {
            seriesId = TOTAL_SERIES;
        }
        for (UsageWindowReading window : reading.windows()) {
            add(reading.providerId(), seriesId, window.type(), window.usedPercentage(),
                    window.resetsAt(), reading.writtenAt());
        }
        // OpenCode entrega una lectura compuesta: la lectura exterior contiene
        // el total y las hijas contienen una serie por modelo. Registrar ambas
        // partes aquí mantiene el historial como fuente única del gráfico.
        for (UsageReading child : reading.seriesReadings()) {
            add(child);
        }
    }

    /**
     * Añade un punto a la serie identificada por proveedor y tipo de ventana.
     *
     * @param providerId     identificador del proveedor
     * @param type           tipo de ventana
     * @param usedPercentage porcentaje consumido
     * @param writtenAt      instante de registro
     */
    public void add(String providerId, UsageWindowType type, int usedPercentage, Instant writtenAt) {
        add(providerId, null, type, usedPercentage, null, writtenAt);
    }

    /**
     * Añade un punto incluyendo el instante de reinicio de la ventana.
     */
    public void add(String providerId, UsageWindowType type, int usedPercentage,
                    Instant resetsAt, Instant writtenAt) {
        add(providerId, null, type, usedPercentage, resetsAt, writtenAt);
    }

    /** Añade un punto a una serie de modelo (o a la serie {@code Total}). */
    public void add(String providerId, String seriesId, UsageWindowType type,
                    int usedPercentage, Instant writtenAt) {
        add(providerId, seriesId, type, usedPercentage, null, writtenAt);
    }

    /** Añade las ventanas de una serie en una sola lectura. */
    public void add(String providerId, String seriesId,
                    Map<UsageWindowType, Integer> percentages, Instant writtenAt) {
        Objects.requireNonNull(percentages, "Los porcentajes no pueden ser nulos");
        percentages.forEach((type, percentage) -> add(providerId, seriesId, type,
                percentage == null ? 0 : percentage, writtenAt));
    }

    /** Añade un punto a una serie incluyendo el instante de reinicio legado. */
    public void add(String providerId, String seriesId, UsageWindowType type,
                    int usedPercentage, Instant resetsAt, Instant writtenAt) {
        Objects.requireNonNull(providerId, "El proveedor no puede ser nulo");
        Objects.requireNonNull(type, "La ventana no puede ser nula");
        Objects.requireNonNull(writtenAt, "El instante no puede ser nulo");
        Key key = new Key(providerId, seriesId, type);
        List<UsageHistoryPoint> points = series.computeIfAbsent(key, k -> new ArrayList<>());

        if (!points.isEmpty()) {
            UsageHistoryPoint last = points.get(points.size() - 1);
            if (isOpenCode(providerId) || isOpenAi(providerId)) {
                if (last.usedPercentage() == clamp(usedPercentage)
                        || !writtenAt.isAfter(last.writtenAt())) {
                    return;
                }
            } else if (!writtenAt.isAfter(last.writtenAt())) {
                return;
            }
        }

        points.add(new UsageHistoryPoint(clamp(usedPercentage), resetsAt, writtenAt));
        if (points.size() > MAX_POINTS_PER_SERIES) {
            points.remove(0);
        }
    }

    private boolean isOpenCode(String providerId) {
        return "opencode".equalsIgnoreCase(providerId);
    }

    private boolean isOpenAi(String providerId) {
        return "openai".equalsIgnoreCase(providerId);
    }

    /**
     * Devuelve la serie de puntos para un proveedor y ventana.
     *
     * @param providerId identificador del proveedor
     * @param type       tipo de ventana
     * @return lista inmutable de puntos ordenados cronológicamente
     */
    public synchronized List<UsageHistoryPoint> getSeries(String providerId, UsageWindowType type) {
        List<UsageHistoryPoint> legacy = series.get(new Key(providerId, null, type));
        if (legacy != null) {
            return Collections.unmodifiableList(legacy);
        }
        // Acceso compatible con v1: una lectura agregada de OpenCode se
        // consulta sin identificador, aunque internamente sea la serie Total.
        if (isOpenCode(providerId) && series.keySet().stream()
                .noneMatch(key -> providerId.equals(key.providerId())
                        && key.seriesId() != null
                        && !TOTAL_SERIES.equals(key.seriesId()))) {
            return getSeries(providerId, TOTAL_SERIES, type);
        }
        return Collections.unmodifiableList(List.of());
    }

    /** Devuelve la serie de un modelo o de {@code Total}. */
    public synchronized List<UsageHistoryPoint> getSeries(String providerId, String seriesId, UsageWindowType type) {
        return Collections.unmodifiableList(series.getOrDefault(new Key(providerId, seriesId, type), List.of()));
    }

    /**
     * Devuelve todas las series almacenadas.
     *
     * @return mapa inmutable de series
     */
    public synchronized Map<Key, List<UsageHistoryPoint>> getAllSeries() {
        Map<Key, List<UsageHistoryPoint>> result = new HashMap<>();
        series.forEach((key, points) -> result.put(key, Collections.unmodifiableList(new ArrayList<>(points))));
        return Collections.unmodifiableMap(result);
    }

    /**
     * Clave de indexación de una serie por proveedor y tipo de ventana.
     */
    public record Key(String providerId, String seriesId, UsageWindowType type) {
        /** Clave compatible con las series únicas de v1. */
        public Key(String providerId, UsageWindowType type) {
            this(providerId, null, type);
        }
    }

    private static int clamp(int percentage) {
        return Math.max(0, Math.min(100, percentage));
    }
}
