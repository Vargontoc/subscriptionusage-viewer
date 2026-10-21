package es.vargontoc.subscriptionviewer.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Component;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class UsageTableTest {
    @Test
    void rendersHeaderAndAlternatingRows() {
        var model = new ProviderViewModel.TableViewModel("Uso", List.of("Uso", "Reinicia en"),
                List.of(row(text("40 %"), text("1 h")), row(text("50 %"), text("2 h")),
                        row(text("60 %"), text("3 h"))));
        UsageTable table = new UsageTable(model);

        assertEquals(8, table.getComponentCount());
        assertEquals(Color.BLACK, table.getComponent(0).getBackground());
        assertEquals(new Color(240, 240, 240), ((JPanel) table.getComponent(2)).getBackground());
        assertEquals(Color.WHITE, ((JPanel) table.getComponent(4)).getBackground());
        assertFalse(table.isFocusable());
    }

    @Test
    void rendersHeaderWithoutFooterWhenThereAreNoRows() {
        UsageTable table = new UsageTable(new ProviderViewModel.TableViewModel(
                "Uso", List.of("Uso", "Reinicia en"), List.of()));

        assertEquals(2, table.getComponentCount());
    }

    @Test
    void rendersNaAsTextAndPercentageAsProgressBar() {
        var row = new ProviderViewModel.RowViewModel("",
                new ProviderViewModel.CellViewModel(ProviderViewModel.CellViewModel.CellType.NA,
                        null, "ignored"),
                new ProviderViewModel.CellViewModel(ProviderViewModel.CellViewModel.CellType.PERCENTAGE,
                        40.0, null));
        UsageTable table = new UsageTable(new ProviderViewModel.TableViewModel(
                "Uso", List.of("Modelo", "Uso"), List.of(row)));

        JPanel firstDataCell = (JPanel) table.getComponent(2);
        JPanel secondDataCell = (JPanel) table.getComponent(3);
        assertInstanceOf(JLabel.class, firstDataCell.getComponent(0));
        assertEquals("N/A", ((JLabel) firstDataCell.getComponent(0)).getText());
        assertInstanceOf(UsageProgressBar.class, secondDataCell.getComponent(0));
        assertEquals(40.0, ((UsageProgressBar) secondDataCell.getComponent(0)).getPercentage());
    }

    private static ProviderViewModel.RowViewModel row(ProviderViewModel.CellViewModel first,
                                                       ProviderViewModel.CellViewModel second) {
        return new ProviderViewModel.RowViewModel("", first, second);
    }

    private static ProviderViewModel.CellViewModel text(String value) {
        return new ProviderViewModel.CellViewModel(
                ProviderViewModel.CellViewModel.CellType.TEXT, null, value);
    }
}
