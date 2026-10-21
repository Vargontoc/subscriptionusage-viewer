package es.vargontoc.subscriptionviewer.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import es.vargontoc.subscriptionviewer.usage.CountdownEvaluator;
import es.vargontoc.subscriptionviewer.usage.UsageSnapshot;
import es.vargontoc.subscriptionviewer.usage.UsageSnapshotListener;
import es.vargontoc.subscriptionviewer.usage.UsageSnapshotPublisher;
import jakarta.annotation.PostConstruct;

/** Ventana fija con una pestaña y un contenido independiente por proveedor. */
@Component
public class UsageWindow implements UsageSnapshotListener {
    private static final int REFRESH_DELAY_MS = 1_000;
    private static final int WINDOW_WIDTH = 800;
    private static final int WINDOW_HEIGHT = 600;
    private static final List<String> PROVIDERS = List.of("claude", "openai", "opencode", "gemini");

    private final UsageSnapshotPublisher publisher;
    private final CountdownEvaluator evaluator;
    private final Clock clock;

    private JDialog dialog;
    private ProviderTabBar tabBar;
    private JPanel contentPanel;
    private CardLayout cardLayout;
    private final Map<String, ProviderContentPanel> contentPanels = new LinkedHashMap<>();
    /** Último modelo pintado de cada proveedor, para conservar el scroll. */
    private Map<String, ProviderViewModel> previousModels = new HashMap<>();
    private String selectedProvider = "claude";
    private Timer refreshTimer;
    private volatile UsageSnapshot lastSnapshot = new UsageSnapshot();

    @Autowired
    public UsageWindow(UsageSnapshotPublisher publisher, CountdownEvaluator evaluator, Clock clock) {
        this.publisher = publisher;
        this.evaluator = evaluator;
        this.clock = clock;
    }

    /** Constructor de compatibilidad para clientes que antes proporcionaban el historial. */
    public UsageWindow(UsageSnapshotPublisher publisher, CountdownEvaluator evaluator, Object ignoredHistory) {
        this(publisher, evaluator, java.time.Clock.systemDefaultZone());
    }

    @PostConstruct
    public void subscribe() {
        publisher.addListener(this);
    }

    public void show() {
        SwingUtilities.invokeLater(() -> {
            ensureWindowCreated();
            updateView(lastSnapshot);
            dialog.setVisible(true);
            refreshTimer.start();
        });
    }

    public void hide() {
        SwingUtilities.invokeLater(() -> {
            if (dialog != null) {
                dialog.setVisible(false);
            }
            if (refreshTimer != null) {
                refreshTimer.stop();
            }
        });
    }

    public boolean isVisible() {
        return dialog != null && dialog.isVisible();
    }

    @Override
    public void onSnapshotChanged(UsageSnapshot snapshot) {
        lastSnapshot = snapshot;
        SwingUtilities.invokeLater(() -> {
            if (dialog != null) {
                updateView(snapshot);
            }
        });
    }

    private void ensureWindowCreated() {
        if (dialog != null) {
            return;
        }
        if (GraphicsEnvironment.isHeadless()) {
            throw new java.awt.HeadlessException("No hay entorno gráfico disponible");
        }

        dialog = new JDialog((Frame) null, "Subscription Usage Viewer", false);
        dialog.setResizable(false);
        dialog.setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        dialog.setLocationRelativeTo(null);
        dialog.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                if (refreshTimer != null) {
                    refreshTimer.stop();
                }
            }
        });

        tabBar = new ProviderTabBar();
        tabBar.setSelectedProvider(selectedProvider);
        tabBar.setHideListener(this::hide);
        tabBar.addSelectionListener(this::selectProvider);

        cardLayout = new CardLayout();
        contentPanel = new JPanel(cardLayout);
        for (String provider : PROVIDERS) {
            ProviderContentPanel panel = new ProviderContentPanel();
            contentPanels.put(provider, panel);
            contentPanel.add(panel, provider);
        }
        cardLayout.show(contentPanel, selectedProvider);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(tabBar, BorderLayout.NORTH);
        mainPanel.add(contentPanel, BorderLayout.CENTER);
        dialog.setContentPane(mainPanel);

        refreshTimer = new Timer(REFRESH_DELAY_MS, event -> {
            if (dialog.isVisible()) {
                updateView(lastSnapshot);
            }
        });
        refreshTimer.setRepeats(true);
    }

    private void selectProvider(String providerId) {
        if (!contentPanels.containsKey(providerId)) {
            return;
        }
        selectedProvider = providerId;
        if (cardLayout != null) {
            cardLayout.show(contentPanel, providerId);
        }
    }

    private void updateView(UsageSnapshot snapshot) {
        if (tabBar == null) {
            return;
        }
        Map<String, ProviderViewModel> models = new LinkedHashMap<>();
        for (String provider : PROVIDERS) {
            models.put(provider, ProviderViewModel.from(provider, snapshot, clock, evaluator));
        }
        tabBar.setProviderStates(models);
        tabBar.setSelectedProvider(selectedProvider);
        for (String provider : PROVIDERS) {
            ProviderViewModel model = models.get(provider);
            ProviderViewModel previousModel = previousModels.get(provider);
            if (!model.equals(previousModel)) {
                ProviderContentPanel panel = contentPanels.get(provider);
                if (panel != null) {
                    panel.setModel(model);
                }
            }
        }
        previousModels = new HashMap<>(models);
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    // Accesos de inspección deliberadamente limitados a la nueva composición.
    public ProviderTabBar getTabBar() {
        ensureWindowCreated();
        return tabBar;
    }

    /** Devuelve el panel del proveedor, útil para inspeccionar la vista activa. */
    public ProviderContentPanel getContentPanel(String providerId) {
        ensureWindowCreated();
        return contentPanels.get(providerId);
    }

    public JPanel getContentPanel() {
        ensureWindowCreated();
        return contentPanel;
    }

    public Map<String, ProviderContentPanel> getContentPanels() {
        ensureWindowCreated();
        return Map.copyOf(contentPanels);
    }

    /** @deprecated la composición ya no contiene tarjetas. */
    @Deprecated
    public JPanel getCardsPanel() {
        return getContentPanel();
    }

    /** @deprecated la composición ya no contiene un estado neutro global. */
    @Deprecated
    public javax.swing.JLabel getNeutralLabel() {
        ensureWindowCreated();
        return new javax.swing.JLabel();
    }

    /** @deprecated el gráfico se conserva para la siguiente especificación, pero no se crea aquí. */
    @Deprecated
    public UsageChartPanel getChartPanel() {
        return null;
    }
}
