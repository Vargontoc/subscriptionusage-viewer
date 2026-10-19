package es.vargontoc.subscriptionviewer.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;

import es.vargontoc.subscriptionviewer.usage.UsageHistory;
import es.vargontoc.subscriptionviewer.usage.UsageHistoryPoint;
import es.vargontoc.subscriptionviewer.usage.UsageWindowType;

/**
 * Panel de pintado propio que muestra la evolución del uso por ventana.
 *
 * <p>Recibe series inmutables del historial, escala el eje horizontal al
 * intervalo temporal disponible y el vertical al rango 0-100 de porcentaje, y
 * dibuja líneas y puntos sin depender de librerías externas. Las series vacías
 * se representan simplemente con los ejes, sin mensajes ni excepciones.</p>
 */
public class UsageChartPanel extends JPanel {

    private static final String OPENAI_PROVIDER_ID = "openai";

    private static final Color FIVE_HOUR_COLOR = new Color(0x22, 0x66, 0xCC);
    private static final Color SEVEN_DAY_COLOR = new Color(0x22, 0x99, 0x66);
    private static final Color MONTH_COLOR = new Color(0xCC, 0x77, 0x22);
    private static final Color AXIS_COLOR = Color.GRAY;
    private static final int POINT_RADIUS = 3;

    private Map<UsageHistory.Key, List<UsageHistoryPoint>> series = Map.of();

    /**
     * Actualiza las series a pintar y solicita un repintado.
     *
     * @param series mapa de series por proveedor y tipo de ventana
     */
    public void updateSeries(Map<UsageHistory.Key, List<UsageHistoryPoint>> series) {
        this.series = copySeries(series);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Insets insets = getInsets();
        int width = getWidth() - insets.left - insets.right;
        int height = getHeight() - insets.top - insets.bottom;
        int left = insets.left;
        int top = insets.top;

        drawAxes(g2d, left, top, width, height);

        if (series.isEmpty()) {
            return;
        }

        Instant minTime = minWrittenAt();
        Instant maxTime = maxWrittenAt();
        if (minTime == null || maxTime == null) {
            return;
        }
        double timeSpan = secondsBetween(minTime, maxTime);
        if (timeSpan <= 0) {
            timeSpan = 1.0;
        }

        for (Map.Entry<UsageHistory.Key, List<UsageHistoryPoint>> entry : series.entrySet()) {
            List<UsageHistoryPoint> points = entry.getValue();
            if (points.isEmpty()) {
                continue;
            }
            Color color = colorFor(entry.getKey().type());
            g2d.setColor(color);

            List<Point> screenPoints = new ArrayList<>(points.size());
            for (UsageHistoryPoint point : points) {
                screenPoints.add(toScreen(point, left, top, width, height, minTime, timeSpan));
            }

            for (int i = 1; i < screenPoints.size(); i++) {
                Point a = screenPoints.get(i - 1);
                Point b = screenPoints.get(i);
                g2d.drawLine(a.x, a.y, b.x, b.y);
            }

            for (Point p : screenPoints) {
                g2d.fillOval(p.x - POINT_RADIUS, p.y - POINT_RADIUS, POINT_RADIUS * 2, POINT_RADIUS * 2);
            }
        }
    }

    /**
     * Proyecta una serie a coordenadas de pantalla (uso interno y tests).
     *
     * @param providerId identificador del proveedor
     * @param type       tipo de ventana
     * @return puntos de pantalla ordenados temporalmente
     */
    List<ScreenPoint> projectSeries(String providerId, UsageWindowType type) {
        return projectSeries(providerId, null, type);
    }

    /**
     * Proyecta una ventana del gráfico de OpenAI. El historial es deliberadamente
     * efímero: al crear un panel nuevo no hay puntos hasta recibir una lectura.
     */
    List<ScreenPoint> projectOpenAiSeries(UsageWindowType type) {
        return projectSeries(OPENAI_PROVIDER_ID, type);
    }

    /** Proyecta una serie concreta (modelo o Total) a coordenadas de pantalla. */
    List<ScreenPoint> projectSeries(String providerId, String seriesId, UsageWindowType type) {
        List<UsageHistoryPoint> points = series.getOrDefault(
                new UsageHistory.Key(providerId, seriesId, type), List.of());
        if (points.isEmpty()) {
            return List.of();
        }

        Insets insets = getInsets();
        int width = getWidth() - insets.left - insets.right;
        int height = getHeight() - insets.top - insets.bottom;
        int left = insets.left;
        int top = insets.top;

        Instant minTime = minWrittenAt();
        Instant maxTime = maxWrittenAt();
        if (minTime == null || maxTime == null) {
            return List.of();
        }
        double timeSpan = secondsBetween(minTime, maxTime);
        if (timeSpan <= 0) {
            timeSpan = 1.0;
        }

        List<ScreenPoint> result = new ArrayList<>(points.size());
        for (UsageHistoryPoint point : points) {
            Point p = toScreen(point, left, top, width, height, minTime, timeSpan);
            result.add(new ScreenPoint(p.x, p.y));
        }
        return Collections.unmodifiableList(result);
    }

    private void drawAxes(Graphics2D g2d, int left, int top, int width, int height) {
        g2d.setColor(AXIS_COLOR);
        g2d.drawLine(left, top + height, left + width, top + height);
        g2d.drawLine(left, top, left, top + height);
    }

    private Point toScreen(UsageHistoryPoint point, int left, int top, int width, int height,
                           Instant minTime, double timeSpan) {
        double timeRatio = secondsBetween(minTime, point.writtenAt()) / timeSpan;
        int x = left + (int) Math.round(timeRatio * width);
        int y = top + height - (int) Math.round((point.usedPercentage() / 100.0) * height);
        return new Point(x, y);
    }

    private Instant minWrittenAt() {
        return series.values().stream()
                .flatMap(List::stream)
                .map(e -> e.writtenAt())
                .min(Instant::compareTo)
                .orElse(null);
    }

    private Instant maxWrittenAt() {
        return series.values().stream()
                .flatMap(List::stream)
                .map(e -> e.writtenAt())
                .max(Instant::compareTo)
                .orElse(null);
    }

    private double secondsBetween(Instant start, Instant end) {
        return (end.getEpochSecond() - start.getEpochSecond())
                + (end.getNano() - start.getNano()) / 1_000_000_000.0;
    }

    private Color colorFor(UsageWindowType type) {
        return switch (type) {
            case FIVE_HOUR -> FIVE_HOUR_COLOR;
            case SEVEN_DAY -> SEVEN_DAY_COLOR;
            case ROLLING -> FIVE_HOUR_COLOR;
            case WEEKLY -> SEVEN_DAY_COLOR;
            case MONTHLY -> MONTH_COLOR;
        };
    }

    private Map<UsageHistory.Key, List<UsageHistoryPoint>> copySeries(
            Map<UsageHistory.Key, List<UsageHistoryPoint>> source) {
        Map<UsageHistory.Key, List<UsageHistoryPoint>> copy = new HashMap<>();
        source.forEach((key, points) -> {
            List<UsageHistoryPoint> ordered = new ArrayList<>(points);
            ordered.sort(Comparator.comparing(e -> e.writtenAt()));
            copy.put(key, List.copyOf(ordered));
        });
        return Collections.unmodifiableMap(copy);
    }

    record ScreenPoint(int x, int y) {
    }
}
