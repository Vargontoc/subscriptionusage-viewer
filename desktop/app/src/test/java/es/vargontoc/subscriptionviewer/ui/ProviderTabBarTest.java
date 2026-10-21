package es.vargontoc.subscriptionviewer.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.junit.jupiter.api.Test;

class ProviderTabBarTest {
    @Test
    void muestraLasCuatroPestanasEnOrdenConIconoYNombre() {
        ProviderTabBar bar = new ProviderTabBar();
        assertTrue(bar.getComponentCount() > 4);
        assertEquals(List.of("Claude", "OpenAI", "OpenCode", "Gemini"),
                tabPanels(bar).stream()
                        .map(tab -> ((JLabel) tab.getComponent(1)).getText())
                        .toList());
        for (JPanel component : tabPanels(bar)) {
            assertNotNull(findLabel((Container) component, true).getIcon());
        }
    }

    @Test
    void seleccionaUnaPestanaAunqueEsteEnGris() {
        ProviderTabBar bar = new ProviderTabBar();
        AtomicReference<String> selected = new AtomicReference<>();
        bar.addSelectionListener(selected::set);
        JPanel gemini = tabPanels(bar).get(3);
        gemini.dispatchEvent(new MouseEvent(gemini, MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(), 0, 2, 2, 1, false));
        assertEquals("gemini", bar.getSelectedProvider());
        assertEquals("gemini", selected.get());
        assertTrue(gemini.getBorder().getBorderInsets(gemini).bottom > 0);
    }

    @Test
    void estadosNoDataSePintanGrisesSinOcultarPestanas() {
        ProviderTabBar bar = new ProviderTabBar();
        bar.setProviderStates(Map.of());
        assertEquals(4, countTabs(bar));
        for (JPanel component : tabPanels(bar)) {
            assertEquals(new java.awt.Color(220, 220, 220), component.getBackground());
        }
    }

    @Test
    void colocaElBotonCerrarTrasUnHuecoFlexible() {
        ProviderTabBar bar = new ProviderTabBar();

        Component glue = java.util.Arrays.stream(bar.getComponents())
                .filter(component -> component instanceof javax.swing.Box.Filler
                        && component.getPreferredSize().width == 0)
                .findFirst().orElseThrow();
        assertTrue(java.util.Arrays.asList(bar.getComponents()).indexOf(glue)
                < java.util.Arrays.asList(bar.getComponents()).indexOf(findCloseButton(bar)));
        assertEquals("closeButton", findCloseButton(bar).getName());
    }

    @Test
    void elBotonCerrarNotificaOcultarSinTerminarLaAplicacion() {
        ProviderTabBar bar = new ProviderTabBar();
        AtomicBoolean hidden = new AtomicBoolean();
        bar.setHideListener(() -> hidden.set(true));

        findCloseButton(bar).doClick();

        assertTrue(hidden.get());
    }

    @Test
    void elBotonCerrarEstaSiempreVisibleYActivo() {
        ProviderTabBar bar = new ProviderTabBar();
        JButton close = findCloseButton(bar);
        java.awt.Color background = close.getBackground();

        bar.setProviderStates(Map.of());

        JButton rebuiltClose = findCloseButton(bar);
        assertTrue(rebuiltClose.isVisible());
        assertTrue(rebuiltClose.isEnabled());
        assertEquals(background, rebuiltClose.getBackground());
    }

    private static long countTabs(ProviderTabBar bar) {
        return tabPanels(bar).size();
    }

    private static List<JPanel> tabPanels(ProviderTabBar bar) {
        return java.util.Arrays.stream(bar.getComponents())
                .filter(JPanel.class::isInstance)
                .map(JPanel.class::cast)
                .toList();
    }

    private static JButton findCloseButton(ProviderTabBar bar) {
        return (JButton) java.util.Arrays.stream(bar.getComponents())
                .filter(JButton.class::isInstance)
                .findFirst().orElseThrow();
    }

    private static JLabel findLabel(Container container, boolean icon) {
        for (Component child : container.getComponents()) {
            if (child instanceof JLabel label && (!icon || label.getIcon() != null)) {
                return label;
            }
        }
        throw new AssertionError("No se encontró el icono");
    }
}
