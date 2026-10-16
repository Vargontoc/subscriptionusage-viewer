package es.vargontoc.subscriptionviewer.ui;

import es.vargontoc.subscriptionviewer.usage.CountdownEvaluator;
import es.vargontoc.subscriptionviewer.usage.CountdownResult;
import es.vargontoc.subscriptionviewer.usage.UsageReading;
import es.vargontoc.subscriptionviewer.usage.UsageWindowReading;
import es.vargontoc.subscriptionviewer.usage.UsageWindowType;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.GridLayout;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

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
    private final JLabel fiveHourLabel;
    private final JLabel sevenDayLabel;
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
        fiveHourLabel = new JLabel();
        sevenDayLabel = new JLabel();
        writtenAtLabel = new JLabel();

        add(providerLabel);
        add(fiveHourLabel);
        add(sevenDayLabel);
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
        fiveHourLabel.setText(fiveHourText != null ? fiveHourText : "");
        sevenDayLabel.setText(sevenDayText != null ? sevenDayText : "");

        LocalDateTime localTime = LocalDateTime.ofInstant(reading.writtenAt(), ZoneId.systemDefault());
        writtenAtLabel.setText("Datos de: " + localTime.format(TIME_FORMATTER));
    }

    /**
     * Indica si la tarjeta tiene al menos una ventana vigente mostrada.
     *
     * @return {@code true} si hay alguna ventana visible
     */
    public boolean hasVisibleWindows() {
        return !fiveHourLabel.getText().isEmpty() || !sevenDayLabel.getText().isEmpty();
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
