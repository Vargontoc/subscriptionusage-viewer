package es.vargontoc.subscriptionviewer.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Fila fija de proveedores que puede reutilizar {@link UsageWindow}. */
public class ProviderTabBar extends JPanel {
    private static final Color NORMAL_BACKGROUND = Color.WHITE;
    private static final Color GREY_BACKGROUND = new Color(220, 220, 220);
    private static final Color GREY_TEXT = Color.GRAY;
    private static final Color SELECTION_INDICATOR = Color.BLACK;
    private static final int TAB_PADDING = 10;
    private static final int TAB_GAP = 5;
    private static final int ICON_SIZE = 22;

    private static final List<TabDefinition> TABS = List.of(
            new TabDefinition("claude", "Claude", "/icons/claude.png"),
            new TabDefinition("openai", "OpenAI", "/icons/openai.png"),
            new TabDefinition("opencode", "OpenCode", "/icons/opencode.png"),
            new TabDefinition("gemini", "Gemini", "/icons/gemini.png"));

    private final List<Consumer<String>> selectionListeners = new ArrayList<>();
    private JButton closeButton;
    private Runnable hideListener;
    private Map<String, ProviderViewModel> providerStates = Map.of();
    private String selectedProvider = "claude";

    public ProviderTabBar() {
        super();
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setOpaque(true);
        setBackground(NORMAL_BACKGROUND);
        createCloseButton();
        rebuildTabs();
    }

    /** Actualiza el estado visual de las cuatro pestañas sin cambiar la selección. */
    public void setProviderStates(Map<String, ProviderViewModel> models) {
        providerStates = models == null ? Map.of() : new LinkedHashMap<>(models);
        rebuildTabs();
    }

    public void setSelectedProvider(String providerId) {
        if (providerId == null || TABS.stream().noneMatch(tab -> tab.id().equals(providerId))) {
            return;
        }
        if (!Objects.equals(selectedProvider, providerId)) {
            selectedProvider = providerId;
            rebuildTabs();
        }
    }

    public String getSelectedProvider() {
        return selectedProvider;
    }

    /** Indica si la pestaña del proveedor está mostrando un estado sin datos. */
    public boolean isProviderGrey(String providerId) {
        ProviderViewModel model = providerStates.get(providerId);
        return model == null || model.state() != ProviderViewModel.PresentationState.DATA;
    }

    public void addSelectionListener(Consumer<String> listener) {
        if (listener != null) {
            selectionListeners.add(listener);
        }
    }

    /** Configura el callback que se ejecuta al solicitar ocultar la ventana. */
    public void setHideListener(Runnable listener) {
        hideListener = listener;
    }

    private void createCloseButton() {
        closeButton = new JButton("✕");
        closeButton.setName("closeButton");
        closeButton.setFocusable(false);
        closeButton.setBorderPainted(false);
        closeButton.setContentAreaFilled(false);
        closeButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        closeButton.addActionListener(event -> {
            if (hideListener != null) {
                hideListener.run();
            }
        });
    }

    private void rebuildTabs() {
        removeAll();
        for (int index = 0; index < TABS.size(); index++) {
            TabDefinition definition = TABS.get(index);
            ProviderViewModel model = providerStates.get(definition.id());
            boolean grey = model == null || model.state() != ProviderViewModel.PresentationState.DATA;
            add(createTab(definition.id(), definition.name(), definition.iconPath(),
                    definition.id().equals(selectedProvider), grey));
            if (index < TABS.size() - 1) {
                add(Box.createRigidArea(new java.awt.Dimension(TAB_GAP, 0)));
            }
        }
        add(Box.createHorizontalGlue());
        add(closeButton);
        add(Box.createRigidArea(new java.awt.Dimension(10, 0)));
        revalidate();
        repaint();
    }

    private JPanel createTab(String providerId, String displayName, String iconPath,
                             boolean isSelected, boolean isGrey) {
        JPanel tab = new JPanel(new BorderLayout(TAB_GAP, 0));
        tab.setName(providerId);
        tab.setOpaque(true);
        tab.setBorder(isSelected
                ? BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 3, 0, SELECTION_INDICATOR),
                        BorderFactory.createEmptyBorder(TAB_PADDING - 1, TAB_PADDING,
                                TAB_PADDING - 2, TAB_PADDING))
                : BorderFactory.createEmptyBorder(TAB_PADDING, TAB_PADDING,
                        TAB_PADDING, TAB_PADDING));
        tab.setBackground(isGrey ? GREY_BACKGROUND : NORMAL_BACKGROUND);

        ImageIcon icon = loadIcon(iconPath);
        JLabel iconLabel = new JLabel(isGrey ? toGreyIcon(icon) : icon);
        JLabel textLabel = new JLabel(displayName);
        Color textColor = isGrey ? GREY_TEXT : Color.BLACK;
        iconLabel.setForeground(textColor);
        textLabel.setForeground(textColor);
        textLabel.setFont(textLabel.getFont().deriveFont(Font.PLAIN));
        tab.add(iconLabel, BorderLayout.WEST);
        tab.add(textLabel, BorderLayout.CENTER);

        MouseAdapter click = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                setSelectedProvider(providerId);
                notifyListeners(providerId);
            }
        };
        addMouseListenerRecursively(tab, click);
        return tab;
    }

    private void notifyListeners(String providerId) {
        selectionListeners.forEach(listener -> listener.accept(providerId));
    }

    private static void addMouseListenerRecursively(java.awt.Component component, MouseAdapter listener) {
        component.addMouseListener(listener);
        if (component instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents()) {
                addMouseListenerRecursively(child, listener);
            }
        }
    }

    private ImageIcon loadIcon(String path) {
        try {
            URL url = getClass().getResource(path);
            if (url == null) {
                return null;
            }
            ImageIcon source = new ImageIcon(url);
            Image scaled = source.getImage().getScaledInstance(ICON_SIZE, ICON_SIZE, Image.SCALE_SMOOTH);
            return new ImageIcon(scaled);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static ImageIcon toGreyIcon(ImageIcon icon) {
        if (icon == null) {
            return null;
        }
        Image source = icon.getImage();
        BufferedImage grey = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = grey.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        for (int y = 0; y < grey.getHeight(); y++) {
            for (int x = 0; x < grey.getWidth(); x++) {
                int argb = grey.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xff;
                int value = (int) (0.299 * ((argb >>> 16) & 0xff)
                        + 0.587 * ((argb >>> 8) & 0xff) + 0.114 * (argb & 0xff));
                grey.setRGB(x, y, (alpha << 24) | (value << 16) | (value << 8) | value);
            }
        }
        return new ImageIcon(grey.getScaledInstance(ICON_SIZE, ICON_SIZE, Image.SCALE_SMOOTH));
    }

    private record TabDefinition(String id, String name, String iconPath) {
    }
}
