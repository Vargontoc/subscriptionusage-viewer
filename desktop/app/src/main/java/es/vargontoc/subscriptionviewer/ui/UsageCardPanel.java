package es.vargontoc.subscriptionviewer.ui;

import java.awt.GridLayout;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

import es.vargontoc.subscriptionviewer.usage.CountdownEvaluator;
import es.vargontoc.subscriptionviewer.usage.CountdownResult;
import es.vargontoc.subscriptionviewer.usage.UsageProviderState;
import es.vargontoc.subscriptionviewer.usage.UsageReading;
import es.vargontoc.subscriptionviewer.usage.UsageWindowReading;
import es.vargontoc.subscriptionviewer.usage.UsageWindowType;

/**
 * Tarjeta visual con la información de uso de un proveedor.
 *
 * <p>Filtra las ventanas que ya han caducado y muestra el tiempo restante de
 * las vigentes calculado por {@link CountdownEvaluator}.</p>
 */
public class UsageCardPanel extends JPanel {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final CountdownEvaluator evaluator;
    private final JLabel providerLabel;
    private final Map<UsageWindowType, JLabel> windowLabels = new EnumMap<>(UsageWindowType.class);
    private final JLabel statusLabel;
    private final JLabel writtenAtLabel;

    /**
     * Crea una tarjeta para el proveedor indicado.
     *
     * @param providerId identificador del proveedor
     * @param evaluator  evaluador de cuenta atrás para calcular vigencia
     */
    public UsageCardPanel(String providerId, CountdownEvaluator evaluator) {
        this.evaluator = evaluator;
        setLayout(new GridLayout(0, 1, 4, 4));
        setBorder(BorderFactory.createTitledBorder(capitalize(providerId)));

        providerLabel = new JLabel(capitalize(providerId));
        statusLabel = new JLabel();
        writtenAtLabel = new JLabel();

        add(providerLabel);
        for (UsageWindowType type : UsageWindowType.values()) {
            JLabel label = new JLabel();
            windowLabels.put(type, label);
            add(label);
        }
        add(statusLabel);
        add(writtenAtLabel);
    }

    /**
     * Actualiza la tarjeta con una lectura de uso.
     *
     * <p>Las ventanas cuyo reinicio ya haya pasado se omiten. Si no queda
     * ninguna ventana vigente, todos los campos de ventana quedan vacíos.</p>
     *
     * @param reading lectura del proveedor
     */
    public void update(UsageReading reading) {
        updateLegacy(reading);
    }

    /** Actualiza la tarjeta desde el estado publicado, nunca desde credenciales. */
    public void update(UsageProviderState state) {
        UsageReading reading = state.reading();
        providerLabel.setText(capitalize(reading != null ? reading.providerId() : "OpenCode"));

        if (reading != null && "opencode".equalsIgnoreCase(reading.providerId())) {
            updateOpenCode(reading);
            return;
        }
        if (reading != null && "claude".equalsIgnoreCase(reading.providerId())
                && state.estado() == UsageProviderState.Estado.CORRECTO) {
            updateLegacy(reading);
            statusLabel.setText("");
            return;
        }

        windowLabels.values().forEach(label -> label.setText(""));
        if (reading != null) {
            for (UsageWindowReading window : reading.windows()) {
                CountdownResult result = evaluator.evaluate(window.resetsAt());
                if (!result.valid()) {
                    continue;
                }
                JLabel label = windowLabels.get(window.type());
                if (label != null) {
                    label.setText(windowName(window.type()) + ": " + percentageText(window.usedPercentage())
                            + " (" + result.formattedRemainingTime() + ")");
                }
            }
        }

        statusLabel.setText(statusText(state.estado()));
        if (state.lastSuccessfulQuery() == null) {
            writtenAtLabel.setText("");
        } else {
            LocalDateTime localTime = LocalDateTime.ofInstant(state.lastSuccessfulQuery(), ZoneId.systemDefault());
            writtenAtLabel.setText("Última actualización: " + localTime.format(TIME_FORMATTER));
        }
    }

    private void updateOpenCode(UsageReading reading) {
        windowLabels.values().forEach(label -> label.setText(""));
        statusLabel.setText("");
        providerLabel.setText("OpenCode");
        removeOpenCodeRows();
        List<UsageReading> rows = reading.seriesReadings();
        for (UsageReading row : rows) {
            if (row == null || row.seriesId() == null
                    || row.windows().stream().noneMatch(window -> window.usedPercentage() > 0)) continue;
            JLabel label = new JLabel(row.seriesId() + "  " + row.windows().stream()
                    .map(window -> windowName(window.type()) + ": " + window.usedPercentage() + "%")
                    .reduce((left, right) -> left + " | " + right).orElse(""));
            label.putClientProperty("opencode-row", Boolean.TRUE);
            add(label, getComponentCount() - 1);
        }
        LocalDateTime localTime = LocalDateTime.ofInstant(reading.writtenAt(), ZoneId.systemDefault());
        writtenAtLabel.setText("Última actualización: " + localTime.format(TIME_FORMATTER));
        revalidate();
        repaint();
    }

    private void removeOpenCodeRows() {
        for (int i = getComponentCount() - 1; i >= 0; i--) {
            if (getComponent(i) instanceof JLabel label
                    && Boolean.TRUE.equals(label.getClientProperty("opencode-row"))) remove(i);
        }
    }

    /** Compatibilidad con el renderizado histórico de Claude. */
    private void updateLegacy(UsageReading reading) {
        providerLabel.setText(capitalize(reading.providerId()));

        String fiveHourText = null;
        String sevenDayText = null;
        for (UsageWindowReading window : reading.windows()) {
            CountdownResult result = evaluator.evaluate(window.resetsAt());
            if (!result.valid()) {
                continue;
            }
            String text = percentageText(window.usedPercentage()) + " (" + formatDuration(result.remainingTime()) + ")";
            if (window.type() == UsageWindowType.FIVE_HOUR) {
                fiveHourText = "Sesion: " + text;
            } else if (window.type() == UsageWindowType.SEVEN_DAY) {
                sevenDayText = "Semanal: " + text;
            }
        }
        windowLabels.get(UsageWindowType.FIVE_HOUR).setText(fiveHourText != null ? fiveHourText : "");
        windowLabels.get(UsageWindowType.SEVEN_DAY).setText(sevenDayText != null ? sevenDayText : "");

        LocalDateTime localTime = LocalDateTime.ofInstant(reading.writtenAt(), ZoneId.systemDefault());
        writtenAtLabel.setText("Datos de: " + localTime.format(TIME_FORMATTER));
    }

    /**
     * Indica si la tarjeta tiene al menos una ventana vigente mostrada.
     *
     * @return {@code true} si hay alguna ventana visible
     */
    public boolean hasVisibleWindows() {
        boolean rows = java.util.stream.IntStream.range(0, getComponentCount())
                .mapToObj(this::getComponent)
                .filter(component -> component instanceof JLabel)
                .map(component -> (JLabel) component)
                .anyMatch(label -> Boolean.TRUE.equals(label.getClientProperty("opencode-row"))
                        && !label.getText().isEmpty());
        return rows || windowLabels.values().stream().anyMatch(label -> !label.getText().isEmpty());
    }

    public boolean hasMessage() {
        return !statusLabel.getText().isEmpty();
    }

    private String statusText(UsageProviderState.Estado estado) {
        return switch (estado) {
            case DESACTUALIZADO -> "Datos desactualizados";
            case SIN_DATOS -> "No hay datos de uso disponibles en este momento.";
            case CORRECTO -> "";
        };
    }

    private String windowName(UsageWindowType type) {
        return switch (type) {
            case FIVE_HOUR, ROLLING -> "Rolling";
            case SEVEN_DAY, WEEKLY -> "Semanal";
            case MONTHLY -> "Mensual";
        };
    }

    private String percentageText(int percentage) {
        return percentage + "%";
    }

    private String formatDuration(Duration duration) {
        long hours = duration.toHours();
        int minutes = duration.toMinutesPart();
        int seconds = duration.toSecondsPart();
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    private static String capitalize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
