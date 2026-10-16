package es.vargontoc.subscriptionviewer.usage;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Historial en memoria de la evolución de uso por proveedor y ventana.
 *
 * <p>Acumula puntos solo cuando el instante de registro es estrictamente
 * posterior al último representado. Cada serie tiene un tamaño máximo
 * documentado; al superarlo se descartan los puntos más antiguos. El historial
 * no persiste ni se reutiliza entre instancias.</p>
 */
@Component
public class UsageHistory {

    /**
     * Número máximo de puntos que conserva cada serie temporal.
     */
    public static final int MAX_POINTS_PER_SERIES = 100;

    private final Map<Key, List<UsageHistoryPoint>> series = new HashMap<>();

    /**
     * Añade los puntos de una lectura al historial.
     *
     * <p>Solo se conserva un punto por ventana cuando {@code writtenAt} es
     * estrictamente posterior al último punto de esa serie.</p>
     *
     * @param reading lectura de uso a incorporar
     */
    public void add(UsageReading reading) {
        Objects.requireNonNull(reading, "La lectura no puede ser nula");
        for (UsageWindowReading window : reading.windows()) {
            add(reading.providerId(), window.type(), window.usedPercentage(), reading.writtenAt());
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
        Key key = new Key(providerId, type);
        List<UsageHistoryPoint> points = series.computeIfAbsent(key, k -> new ArrayList<>());

        if (!points.isEmpty() && !writtenAt.isAfter(points.get(points.size() - 1).writtenAt())) {
            return;
        }

        points.add(new UsageHistoryPoint(usedPercentage, writtenAt));
        if (points.size() > MAX_POINTS_PER_SERIES) {
            points.remove(0);
        }
    }

    /**
     * Devuelve la serie de puntos para un proveedor y ventana.
     *
     * @param providerId identificador del proveedor
     * @param type       tipo de ventana
     * @return lista inmutable de puntos ordenados cronológicamente
     */
    public List<UsageHistoryPoint> getSeries(String providerId, UsageWindowType type) {
        return Collections.unmodifiableList(series.getOrDefault(new Key(providerId, type), List.of()));
    }

    /**
     * Devuelve todas las series almacenadas.
     *
     * @return mapa inmutable de series
     */
    public Map<Key, List<UsageHistoryPoint>> getAllSeries() {
        Map<Key, List<UsageHistoryPoint>> result = new HashMap<>();
        series.forEach((key, points) -> result.put(key, Collections.unmodifiableList(new ArrayList<>(points))));
        return Collections.unmodifiableMap(result);
    }

    /**
     * Clave de indexación de una serie por proveedor y tipo de ventana.
     */
    public record Key(String providerId, UsageWindowType type) {
    }
}
