package es.vargontoc.subscriptionviewer.tray;

import es.vargontoc.subscriptionviewer.lifecycle.LifecycleCoordinator;
import es.vargontoc.subscriptionviewer.ui.UsageWindow;
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
 * <p>Verifican que el menú expone la opción de mostrar uso antes de la opción
 * de cierre, que ambas delegan correctamente y que la ventana se reutiliza al
 * repetir la acción de mostrar.</p>
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
    void popupMenuContainsShowUsageBeforeExitItem() {
        LifecycleCoordinator coordinator = mock(LifecycleCoordinator.class);
        UsageWindow usageWindow = mock(UsageWindow.class);
        TrayMenu trayMenu = new TrayMenu(coordinator, usageWindow);

        PopupMenu popupMenu = trayMenu.getPopupMenu();

        assertThat(popupMenu.getItemCount()).isEqualTo(2);

        MenuItem showUsageItem = popupMenu.getItem(0);
        assertThat(showUsageItem.getLabel()).isEqualTo("Mostrar uso");

        MenuItem exitItem = popupMenu.getItem(1);
        assertThat(exitItem.getLabel()).isEqualTo("Cerrar");
    }

    @Test
    void selectingShowUsageItemShowsWindow() {
        LifecycleCoordinator coordinator = mock(LifecycleCoordinator.class);
        UsageWindow usageWindow = mock(UsageWindow.class);
        TrayMenu trayMenu = new TrayMenu(coordinator, usageWindow);

        MenuItem showUsageItem = trayMenu.getPopupMenu().getItem(0);

        ActionEvent event = new ActionEvent(
                showUsageItem,
                ActionEvent.ACTION_PERFORMED,
                showUsageItem.getActionCommand());

        for (ActionListener listener : showUsageItem.getActionListeners()) {
            listener.actionPerformed(event);
        }

        verify(usageWindow).show();
    }

    @Test
    void selectingShowUsageItemTwiceReusesWindow() {
        LifecycleCoordinator coordinator = mock(LifecycleCoordinator.class);
        UsageWindow usageWindow = mock(UsageWindow.class);
        TrayMenu trayMenu = new TrayMenu(coordinator, usageWindow);

        MenuItem showUsageItem = trayMenu.getPopupMenu().getItem(0);

        ActionEvent event = new ActionEvent(
                showUsageItem,
                ActionEvent.ACTION_PERFORMED,
                showUsageItem.getActionCommand());

        for (ActionListener listener : showUsageItem.getActionListeners()) {
            listener.actionPerformed(event);
            listener.actionPerformed(event);
        }

        verify(usageWindow).show();
    }

    @Test
    void selectingExitItemRequestsShutdown() {
        LifecycleCoordinator coordinator = mock(LifecycleCoordinator.class);
        UsageWindow usageWindow = mock(UsageWindow.class);
        TrayMenu trayMenu = new TrayMenu(coordinator, usageWindow);

        MenuItem exitItem = trayMenu.getPopupMenu().getItem(1);

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
