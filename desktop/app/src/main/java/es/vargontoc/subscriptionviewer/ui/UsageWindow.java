package es.vargontoc.subscriptionviewer.ui;

import es.vargontoc.subscriptionviewer.usage.CountdownEvaluator;
import es.vargontoc.subscriptionviewer.usage.UsageHistory;
import es.vargontoc.subscriptionviewer.usage.UsageReading;
import es.vargontoc.subscriptionviewer.usage.UsageSnapshot;
import es.vargontoc.subscriptionviewer.usage.UsageSnapshotListener;
import es.vargontoc.subscriptionviewer.usage.UsageSnapshotPublisher;
import es.vargontoc.subscriptionviewer.usage.UsageProviderState;
import es.vargontoc.subscriptionviewer.usage.UsageWindowReading;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ventana de escritorio que muestra el uso por proveedor.
 *
 * <p>Se suscribe al {@link UsageSnapshotPublisher} y reconstruye las tarjetas
 * solo para los proveedores presentes en cada snapshot. Si no hay proveedores
 * con datos vigentes, muestra un texto informativo neutro.</p>
 *
 * <p>La creación de componentes Swing se retrasa hasta que la ventana se va a
 * mostrar, para que el bean pueda instanciarse en entornos headless (tests) sin
 * lanzar excepciones. Un temporizador de Swing refresca la presentación cada
 * segundo para actualizar la cuenta atrás y ocultar ventanas que hayan
 * caducado.</p>
 */
@Component
public class UsageWindow implements UsageSnapshotListener {

    private static final String NEUTRAL_TEXT = "No hay datos de uso disponibles en este momento.";
    private static final String NEUTRAL_CARD = "neutral";
    private static final String CARDS_CARD = "cards";
    private static final int REFRESH_DELAY_MS = 1_000;

    private final UsageSnapshotPublisher publisher;
    private final CountdownEvaluator evaluator;
    private final UsageHistory usageHistory;
    private JFrame frame;
    private JPanel cardsPanel;
    private JLabel neutralLabel;
    private JPanel contentPanel;
    private CardLayout cardLayout;
    private UsageChartPanel chartPanel;
    private Timer refreshTimer;
    private final Map<String, UsageCardPanel> cards = new HashMap<>();
    private volatile UsageSnapshot lastSnapshot = new UsageSnapshot();

    public UsageWindow(UsageSnapshotPublisher publisher, CountdownEvaluator evaluator, UsageHistory usageHistory) {
        this.publisher = publisher;
        this.evaluator = evaluator;
        this.usageHistory = usageHistory;
    }

    @PostConstruct
    public void subscribe() {
        publisher.addListener(this);
    }

    /**
     * Muestra la ventana en pantalla.
     *
     * @throws java.awt.HeadlessException si el entorno no admite interfaz gráfica
     */
    public void show() {
        SwingUtilities.invokeLater(() -> {
            ensureWindowCreated();
            updateContent(lastSnapshot);
            frame.setVisible(true);
            refreshTimer.start();
        });
    }

    /**
     * Oculta la ventana sin cerrarla.
     */
    public void hide() {
        SwingUtilities.invokeLater(() -> {
            if (frame != null) {
                frame.setVisible(false);
            }
            if (refreshTimer != null) {
                refreshTimer.stop();
            }
        });
    }

    /**
     * Indica si la ventana está visible.
     *
     * @return {@code true} si la ventana se muestra
     */
    public boolean isVisible() {
        return frame != null && frame.isVisible();
    }

    @Override
    public void onSnapshotChanged(UsageSnapshot snapshot) {
        lastSnapshot = snapshot;
        SwingUtilities.invokeLater(() -> {
            if (frame != null) {
                updateContent(snapshot);
            }
        });
    }

    private void ensureWindowCreated() {
        if (frame != null) {
            return;
        }
        if (GraphicsEnvironment.isHeadless()) {
            throw new java.awt.HeadlessException("No hay entorno gráfico disponible");
        }
        frame = new JFrame("Uso de suscripciones");
        cardLayout = new CardLayout();
        contentPanel = new JPanel(cardLayout);
        cardsPanel = new JPanel();
        neutralLabel = new JLabel(NEUTRAL_TEXT, JLabel.CENTER);

        frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        frame.setSize(400, 300);
        frame.setLayout(new BorderLayout());

        cardsPanel.setLayout(new javax.swing.BoxLayout(cardsPanel, javax.swing.BoxLayout.Y_AXIS));
        contentPanel.add(neutralLabel, NEUTRAL_CARD);
        contentPanel.add(cardsPanel, CARDS_CARD);
        frame.add(contentPanel, BorderLayout.NORTH);

        chartPanel = new UsageChartPanel();
        chartPanel.setPreferredSize(new java.awt.Dimension(0, 150));
        frame.add(chartPanel, BorderLayout.CENTER);

        refreshTimer = new Timer(REFRESH_DELAY_MS, e -> updateContent(lastSnapshot));
        refreshTimer.setRepeats(true);

        // Un snapshot puede haber llegado antes de crear la ventana (por ejemplo,
        // mientras estaba oculta). Renderizarlo ahora evita perder también la
        // actualización del gráfico al abrirla por primera vez.
        updateContent(lastSnapshot);
    }

    private void updateContent(UsageSnapshot snapshot) {
        if (cardsPanel == null) {
            return;
        }
        cardsPanel.removeAll();
        cards.clear();

        boolean anyCard = false;
        Map<String, UsageProviderState> states = new HashMap<>(snapshot.providerStates());
        // Compatibilidad con publicadores antiguos que aún solo entregan lecturas.
        snapshot.readings().forEach((provider, reading) -> states.putIfAbsent(provider,
                new UsageProviderState(UsageProviderState.Estado.CORRECTO, reading, reading.writtenAt())));
        for (Map.Entry<String, UsageProviderState> entry : states.entrySet()) {
            UsageProviderState state = entry.getValue();
            UsageCardPanel card = new UsageProviderCardPanel(entry.getKey(), evaluator);
            card.update(state);
            if (!card.hasVisibleWindows() && !card.hasMessage()) {
                continue;
            }
            cardsPanel.add(card);
            cards.put(entry.getKey(), card);
            anyCard = true;
        }

        if (anyCard) {
            cardsPanel.revalidate();
            cardsPanel.repaint();
            cardLayout.show(contentPanel, CARDS_CARD);
        } else {
            cardLayout.show(contentPanel, NEUTRAL_CARD);
        }

        if (chartPanel != null) {
            // updateContent solo se invoca en EDT (incluido el listener de
            // snapshots), por lo que el modelo Swing se actualiza sin carreras.
            chartPanel.updateSeries(usageHistory.getAllSeries());
        }
    }

    private Optional<UsageReading> filterValidWindows(UsageReading reading) {
        List<UsageWindowReading> valid = new ArrayList<>();
        for (UsageWindowReading window : reading.windows()) {
            if (evaluator.evaluate(window.resetsAt()).valid()) {
                valid.add(window);
            }
        }
        if (valid.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new UsageReading(reading.providerId(), reading.writtenAt(), valid));
    }

    /**
     * Devuelve el panel de tarjetas (uso interno y tests).
     *
     * @return panel contenedor de tarjetas
     */
    public JPanel getCardsPanel() {
        ensureWindowCreated();
        return cardsPanel;
    }

    /**
     * Devuelve la etiqueta neutra (uso interno y tests).
     *
     * @return etiqueta de texto neutro
     */
    public JLabel getNeutralLabel() {
        ensureWindowCreated();
        return neutralLabel;
    }

    /**
     * Devuelve el panel del gráfico (uso interno y tests).
     *
     * @return panel del gráfico de evolución
     */
    public UsageChartPanel getChartPanel() {
        ensureWindowCreated();
        return chartPanel;
    }
}
