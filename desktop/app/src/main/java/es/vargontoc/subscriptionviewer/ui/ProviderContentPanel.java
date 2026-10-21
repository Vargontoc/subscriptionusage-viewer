package es.vargontoc.subscriptionviewer.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;

/** Contenedor visual del estado de un proveedor. */
public class ProviderContentPanel extends JPanel {
    private static final int TABLE_GAP = 20;
    private static final int PADDING = 10;
    private static final Color GRIS_CLARO = new Color(220, 220, 220);
    private static final Color FONDO_DATOS = Color.WHITE;
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private ProviderViewModel model;

    public ProviderContentPanel() {
        setLayout(new BorderLayout());
        setFocusable(false);
    }

    public void setModel(ProviderViewModel model) {
        this.model = Objects.requireNonNull(model, "model");
        removeAll();

        if (model.state() == ProviderViewModel.PresentationState.DATA) {
            setBackground(FONDO_DATOS);
            JLabel updatedAt = new JLabel("última actualización "
                    + TIME_FORMATTER.format(model.updatedAt()));
            updatedAt.setFocusable(false);
            updatedAt.setHorizontalAlignment(SwingConstants.LEFT);
            add(updatedAt, BorderLayout.NORTH);

            JPanel tablesPanel = createTablesPanel(model.tables());
            JScrollPane scrollPane = new JScrollPane(tablesPanel);
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            add(scrollPane, BorderLayout.CENTER);
        } else {
            setBackground(GRIS_CLARO);
            JLabel message = new JLabel(model.errorText());
            message.setFocusable(false);
            message.setHorizontalAlignment(SwingConstants.CENTER);
            message.setVerticalAlignment(SwingConstants.CENTER);

            JPanel centered = new JPanel(new GridBagLayout());
            centered.setOpaque(false);
            centered.setFocusable(false);
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.gridx = 0;
            constraints.gridy = 0;
            constraints.weightx = 1;
            constraints.weighty = 1;
            constraints.fill = GridBagConstraints.BOTH;
            constraints.insets = new Insets(0, 8, 0, 8);
            centered.add(message, constraints);
            add(centered, BorderLayout.CENTER);
        }

        revalidate();
        repaint();
    }

    private JPanel createTablesPanel(java.util.List<ProviderViewModel.TableViewModel> tables) {
        JPanel panel = new JPanel();
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.X_AXIS));
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(PADDING, PADDING,
                PADDING, PADDING));

        for (int i = 0; i < tables.size(); i++) {
            if (i > 0) {
                panel.add(javax.swing.Box.createRigidArea(new Dimension(TABLE_GAP, 0)));
            }
            UsageTable table = new UsageTable();
            table.setModel(tables.get(i));
            table.setAlignmentY(TOP_ALIGNMENT);
            panel.add(table);
        }
        return panel;
    }

    public ProviderViewModel getModel() {
        return model;
    }

    /** Estado de presentación actualmente pintado. */
    public ProviderViewModel.PresentationState getState() {
        return model == null ? null : model.state();
    }

    /** Texto del estado actualmente pintado (vacío cuando hay datos). */
    public String getText() {
        return model == null || model.errorText() == null ? "" : model.errorText();
    }
}
