package es.vargontoc.subscriptionviewer.ui;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Tabla ligera, sin bordes, usada para representar un {@link ProviderViewModel.TableViewModel}. */
public final class UsageTable extends JPanel {
    private static final Color HEADER_BG = Color.BLACK;
    private static final Color HEADER_FG = Color.WHITE;
    private static final Color ROW_EVEN_BG = new Color(240, 240, 240);
    private static final Color ROW_ODD_BG = Color.WHITE;
    private static final int ROW_HEIGHT = 30;
    private static final int CELL_PADDING = 10;

    private ProviderViewModel.TableViewModel model;

    public UsageTable() {
        this(null);
    }

    public UsageTable(ProviderViewModel.TableViewModel model) {
        setFocusable(false);
        setOpaque(false);
        setBorder(null);
        setModel(model);
    }

    public void setModel(ProviderViewModel.TableViewModel model) {
        this.model = model;
        removeAll();
        setLayout(new GridBagLayout());

        List<String> headers = model == null ? List.of() : model.headers();
        if (!headers.isEmpty()) {
            for (int column = 0; column < headers.size(); column++) {
                addCell(new JLabel(headers.get(column), JLabel.CENTER), column, 0,
                        HEADER_BG, HEADER_FG);
            }
            int rowIndex = 1;
            for (ProviderViewModel.RowViewModel row : model.rows()) {
                List<ProviderViewModel.CellViewModel> cells = cellsFor(row, headers.size());
                Color background = rowIndex % 2 == 1 ? ROW_EVEN_BG : ROW_ODD_BG;
                for (int column = 0; column < headers.size(); column++) {
                    addCell(componentFor(cells.get(column)), column, rowIndex,
                            background, Color.BLACK);
                }
                rowIndex++;
            }
        }
        revalidate();
        repaint();
    }

    public ProviderViewModel.TableViewModel getModel() {
        return model;
    }

    private void addCell(java.awt.Component component, int column, int row,
                         Color background, Color foreground) {
        JPanel cell = new JPanel(new GridBagLayout());
        cell.setOpaque(true);
        cell.setBackground(background);
        cell.setBorder(BorderFactory.createEmptyBorder(0, CELL_PADDING, 0, CELL_PADDING));
        cell.setFocusable(false);
        component.setForeground(foreground);
        component.setFocusable(false);
        cell.add(component);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = column;
        constraints.gridy = row;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.CENTER;
        constraints.insets = new Insets(0, 0, 0, 0);
        add(cell, constraints);
    }

    private static List<ProviderViewModel.CellViewModel> cellsFor(
            ProviderViewModel.RowViewModel row, int columnCount) {
        List<ProviderViewModel.CellViewModel> cells = new ArrayList<>();
        if (row.label() != null && !row.label().isEmpty()) {
            cells.add(new ProviderViewModel.CellViewModel(
                    ProviderViewModel.CellViewModel.CellType.TEXT, null, row.label()));
        }
        cells.add(row.percentageCell());
        if (row.timeCell() != null) {
            cells.add(row.timeCell());
        }
        cells.addAll(row.additionalCells());
        while (cells.size() < columnCount) {
            cells.add(new ProviderViewModel.CellViewModel(
                    ProviderViewModel.CellViewModel.CellType.TEXT, null, ""));
        }
        return cells.subList(0, columnCount);
    }

    private static java.awt.Component componentFor(ProviderViewModel.CellViewModel cell) {
        return switch (cell.type()) {
            case TEXT, NA -> new JLabel(cell.type() == ProviderViewModel.CellViewModel.CellType.NA
                    ? "N/A" : nullToEmpty(cell.text()), JLabel.CENTER);
            case PERCENTAGE -> new UsageProgressBar(cell.percentage(), percentageText(cell.percentage()));
        };
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String percentageText(double percentage) {
        DecimalFormat format = new DecimalFormat("0.##",
                DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-ES")));
        return format.format(BigDecimal.valueOf(percentage)) + " %";
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension preferred = super.getPreferredSize();
        int rows = model == null ? 0 : model.rows().size() + (model.headers().isEmpty() ? 0 : 1);
        return new Dimension(preferred.width, rows * ROW_HEIGHT);
    }
}
