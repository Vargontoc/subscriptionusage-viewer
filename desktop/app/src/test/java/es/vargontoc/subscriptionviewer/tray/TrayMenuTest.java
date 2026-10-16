package es.vargontoc.subscriptionviewer.tray;

import es.vargontoc.subscriptionviewer.lifecycle.LifecycleCoordinator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.awt.GraphicsEnvironment;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Pruebas del menú contextual de la bandeja del sistema.
 *
 * <p>Verifican que el menú expone la opción de cierre y que, al seleccionarla,
 * se delega correctamente en {@link LifecycleCoordinator#requestShutdown()}.</p>
 *
 * <p>AWT requiere un entorno gráfico para crear {@link PopupMenu} y
 * {@link MenuItem}; por tanto el test se omite en entornos headless.</p>
 */
@EnabledIf("notHeadless")
class TrayMenuTest {

    static boolean notHeadless() {
        return !GraphicsEnvironment.isHeadless();
    }

    @Test
    void popupMenuContainsExitItem() {
        LifecycleCoordinator coordinator = mock(LifecycleCoordinator.class);
        TrayMenu trayMenu = new TrayMenu(coordinator);

        PopupMenu popupMenu = trayMenu.getPopupMenu();

        assertThat(popupMenu.getItemCount())
                .as("El menú debe contener al menos un elemento")
                .isGreaterThanOrEqualTo(1);

        MenuItem exitItem = popupMenu.getItem(0);
        assertThat(exitItem.getLabel())
                .as("El primer elemento debe ser la opción de cierre")
                .isEqualTo("Cerrar");
    }

    @Test
    void selectingExitItemRequestsShutdown() {
        LifecycleCoordinator coordinator = mock(LifecycleCoordinator.class);
        TrayMenu trayMenu = new TrayMenu(coordinator);

        MenuItem exitItem = trayMenu.getPopupMenu().getItem(0);

        ActionEvent event = new ActionEvent(
                exitItem,
                ActionEvent.ACTION_PERFORMED,
                exitItem.getActionCommand());

        for (ActionListener listener : exitItem.getActionListeners()) {
            listener.actionPerformed(event);
        }

        verify(coordinator).requestShutdown();
    }
}
