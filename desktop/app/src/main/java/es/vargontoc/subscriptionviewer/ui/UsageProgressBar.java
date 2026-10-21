package es.vargontoc.subscriptionviewer.ui;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Objects;

/**
 * Barra de progreso cuyo aspecto no depende del look and feel de Swing.
 *
 * <p>El porcentaje se conserva sin redondear. El texto, por el contrario, es
 * proporcionado por el modelo de vista y puede tener una precisión distinta
 * de la usada para decidir el color.</p>
 */
public final class UsageProgressBar extends JComponent {

    private static final int BORDER_SIZE = 1;
    private static final int DEFAULT_WIDTH = 140;
    private static final int DEFAULT_HEIGHT = 24;

    private double percentage;
    private String text;

    public UsageProgressBar() {
        this(0.0, "");
    }

    public UsageProgressBar(double percentage, String text) {
        setFocusable(false);
        setOpaque(false);
        setPercentage(percentage);
        setText(text);
    }

    public void setPercentage(double percentage) {
        // Además de validar el contrato del componente, esto evita que un
        // NaN produzca un ancho de relleno no determinista al pintar.
        ProgressLevel.of(percentage);
        this.percentage = percentage;
        repaint();
    }

    public void setText(String text) {
        this.text = Objects.requireNonNull(text, "text");
        repaint();
    }

    public double getPercentage() {
        return percentage;
    }

    public String getText() {
        return text;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();
            int interiorWidth = Math.max(0, width - 2 * BORDER_SIZE);
            int interiorHeight = Math.max(0, height - 2 * BORDER_SIZE);

            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);

            ProgressLevel level = ProgressLevel.of(percentage);
            int fillWidth = (int) ((percentage / 100.0) * interiorWidth);
            g.setColor(colorFor(level));
            if (interiorWidth > 0 && interiorHeight > 0 && fillWidth > 0) {
                g.fillRect(BORDER_SIZE, BORDER_SIZE, fillWidth, interiorHeight);
            }

            g.setColor(Color.BLACK);
            if (width > 0 && height > 0) {
                g.drawRect(0, 0, width - 1, height - 1);
            }

            FontMetrics metrics = g.getFontMetrics();
            int textWidth = metrics.stringWidth(text);
            int baseline = (height - metrics.getHeight()) / 2 + metrics.getAscent();
            g.setColor(percentage == 100.0 ? Color.WHITE : Color.BLACK);
            g.drawString(text, (width - textWidth) / 2, baseline);
        } finally {
            g.dispose();
        }
    }

    private static Color colorFor(ProgressLevel level) {
        return switch (level) {
            case VERDE -> Color.GREEN;
            case AMARILLO -> Color.YELLOW;
            case NARANJA -> Color.ORANGE;
            case ROJO -> Color.RED;
            case NEGRO -> Color.BLACK;
        };
    }
}
