package es.vargontoc.subscriptionviewer.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JScrollPane;

import org.junit.jupiter.api.Test;

class ProviderContentPanelTest {
    private static final Instant UPDATED_AT = Instant.parse("2026-10-21T10:32:15Z");

    @Test
    void datosMuestraHoraYFondoBlanco() {
        ProviderContentPanel panel = new ProviderContentPanel();
        panel.setModel(model(ProviderViewModel.PresentationState.DATA, UPDATED_AT));

        String text = labelsText(panel);
        assertTrue(text.contains("última actualización"));
        assertTrue(text.contains(DateTimeFormatter.ofPattern("HH:mm:ss")
                .withZone(ZoneId.systemDefault()).format(UPDATED_AT)));
        assertEquals(Color.WHITE, panel.getBackground());
        assertFalse(text.contains("error"));
    }

    @Test
    void errorClaudeMuestraTextoFormalYFondoGrisSinHora() {
        ProviderContentPanel panel = new ProviderContentPanel();
        panel.setModel(model(ProviderViewModel.PresentationState.ERROR, null));

        String text = labelsText(panel);
        assertEquals(ProviderErrorTexts.errorText(ProviderErrorTexts.CLAUDE,
                es.vargontoc.subscriptionviewer.usage.UsageProviderState.Estado.SIN_DATOS),
                text.trim());
        assertEquals(new Color(220, 220, 220), panel.getBackground());
        assertFalse(text.contains("última actualización"));
    }

    @Test
    void fetchingMuestraMensajeSinTerminosDeError() {
        ProviderContentPanel panel = new ProviderContentPanel();
        panel.setModel(model(ProviderViewModel.PresentationState.FETCHING, null));

        String text = labelsText(panel);
        assertTrue(text.contains("Obteniendo datos…"));
        assertFalse(text.toLowerCase().matches(".*(error|fallo|aviso).*"));
        assertEquals(new Color(220, 220, 220), panel.getBackground());
    }

    @Test
    void cambiarDeFetchingADataActualizaElContenido() {
        ProviderContentPanel panel = new ProviderContentPanel();
        panel.setModel(model(ProviderViewModel.PresentationState.FETCHING, null));
        panel.setModel(model(ProviderViewModel.PresentationState.DATA, UPDATED_AT));

        String text = labelsText(panel);
        assertFalse(text.contains("Obteniendo datos…"));
        assertTrue(text.contains("última actualización"));
    }

    @Test
    void cambiarDeDataAErrorQuitaLaHora() {
        ProviderContentPanel panel = new ProviderContentPanel();
        panel.setModel(model(ProviderViewModel.PresentationState.DATA, UPDATED_AT));
        panel.setModel(model(ProviderViewModel.PresentationState.ERROR, null));

        String text = labelsText(panel);
        assertFalse(text.contains("última actualización"));
        assertTrue(text.contains("No se han podido obtener los datos de uso de Claude"));
    }

    @Test
    void tablasDataSeColocanEnFilaDentroDeUnSoloScroll() {
        ProviderContentPanel panel = new ProviderContentPanel();
        panel.setModel(dataModel(List.of(table("A", 2), table("B", 2), table("C", 2))));
        panel.setSize(900, 300);
        panel.doLayout();

        JScrollPane scroll = scrollPane(panel);
        scroll.doLayout();
        scroll.getViewport().doLayout();
        scroll.getViewport().getView().doLayout();
        List<UsageTable> tables = tables(scroll.getViewport().getView());
        assertEquals(3, tables.size());
        assertEquals(tables.get(0).getY(), tables.get(1).getY());
        assertEquals(tables.get(1).getY(), tables.get(2).getY());
        assertEquals(20, tables.get(1).getX() - tables.get(0).getX()
                - tables.get(0).getWidth(), tables.get(0).getBounds() + " / "
                + tables.get(1).getBounds());
        assertEquals(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER, scroll.getHorizontalScrollBarPolicy());
    }

    @Test
    void muchasFilasUsanUnScrollVerticalCompartido() {
        ProviderContentPanel panel = new ProviderContentPanel();
        panel.setModel(dataModel(List.of(table("A", 40), table("B", 40))));
        panel.setSize(900, 200);
        panel.doLayout();

        JScrollPane scroll = scrollPane(panel);
        scroll.doLayout();
        scroll.getViewport().doLayout();
        assertTrue(scroll.getVerticalScrollBar().isVisible());
        assertEquals(1, scrollPanes(panel).size());
    }

    @Test
    void pocasFilasNoMuestranScrollVertical() {
        ProviderContentPanel panel = new ProviderContentPanel();
        panel.setModel(dataModel(List.of(table("A", 1), table("B", 1))));
        panel.setSize(900, 300);
        panel.doLayout();

        JScrollPane scroll = scrollPane(panel);
        scroll.doLayout();
        scroll.getViewport().doLayout();
        assertFalse(scroll.getVerticalScrollBar().isVisible());
    }

    private static ProviderViewModel dataModel(List<ProviderViewModel.TableViewModel> tables) {
        return new ProviderViewModel(ProviderErrorTexts.CLAUDE,
                ProviderViewModel.PresentationState.DATA, UPDATED_AT, tables);
    }

    private static ProviderViewModel.TableViewModel table(String title, int rowCount) {
        List<ProviderViewModel.RowViewModel> rows = new ArrayList<>();
        for (int i = 0; i < rowCount; i++) {
            rows.add(new ProviderViewModel.RowViewModel("fila " + i,
                    new ProviderViewModel.CellViewModel(
                            ProviderViewModel.CellViewModel.CellType.PERCENTAGE, 40.0, null),
                    new ProviderViewModel.CellViewModel(
                            ProviderViewModel.CellViewModel.CellType.TEXT, null, "1 h")));
        }
        return new ProviderViewModel.TableViewModel(title,
                List.of(title, "Reinicia en"), rows);
    }

    private static JScrollPane scrollPane(Container container) {
        for (Component component : container.getComponents()) {
            if (component instanceof JScrollPane scroll) {
                return scroll;
            }
            if (component instanceof Container child) {
                try {
                    return scrollPane(child);
                } catch (IllegalStateException ignored) {
                    // Continue searching sibling components.
                }
            }
        }
        throw new IllegalStateException("No se encontró el scroll de tablas");
    }

    private static List<JScrollPane> scrollPanes(Container container) {
        List<JScrollPane> result = new ArrayList<>();
        for (Component component : container.getComponents()) {
            if (component instanceof JScrollPane scroll) {
                result.add(scroll);
            }
            if (component instanceof Container child) {
                result.addAll(scrollPanes(child));
            }
        }
        return result;
    }

    private static List<UsageTable> tables(Component component) {
        List<UsageTable> result = new ArrayList<>();
        if (component instanceof UsageTable table) {
            result.add(table);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                result.addAll(tables(child));
            }
        }
        return result;
    }

    private static ProviderViewModel model(ProviderViewModel.PresentationState state,
                                           Instant updatedAt) {
        return new ProviderViewModel(ProviderErrorTexts.CLAUDE, state, updatedAt, List.of());
    }

    private static String labelsText(java.awt.Container container) {
        StringBuilder result = new StringBuilder();
        for (java.awt.Component component : container.getComponents()) {
            if (component instanceof JLabel label) {
                result.append(label.getText()).append(' ');
            }
            if (component instanceof java.awt.Container child) {
                result.append(labelsText(child));
            }
        }
        return result.toString();
    }
}
