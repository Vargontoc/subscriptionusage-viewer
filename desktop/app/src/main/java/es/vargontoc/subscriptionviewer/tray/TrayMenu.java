package es.vargontoc.subscriptionviewer.tray;

import es.vargontoc.subscriptionviewer.lifecycle.LifecycleCoordinator;
import es.vargontoc.subscriptionviewer.ui.UsageWindow;
import org.springframework.stereotype.Component;

import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Menú contextual asociado al icono de bandeja del sistema.
 *
 * <p>Construye un {@link PopupMenu} de AWT con una opción para mostrar la
 * ventana de uso y una opción de cierre. Al activarse la primera delega en
 * {@link UsageWindow#show()}; al activarse la segunda delega en
 * {@link LifecycleCoordinator#requestShutdown()} para iniciar el apagado
 * ordenado de la aplicación.</p>
 */
@Component
public class TrayMenu {

    private static final String SHOW_USAGE_ITEM_LABEL = "Mostrar uso";
    private static final String EXIT_ITEM_LABEL = "Cerrar";

    private final LifecycleCoordinator lifecycleCoordinator;
    private final UsageWindow usageWindow;
    private PopupMenu popupMenu;

    /**
     * Crea el menú contextual con las opciones de mostrar uso y cierre.
     *
     * <p>Los componentes AWT se construyen de forma diferida al solicitar el
     * {@link PopupMenu}, de modo que el bean pueda instanciarse incluso en
     * entornos headless donde no se vaya a mostrar la bandeja.</p>
     *
     * @param lifecycleCoordinator coordinador encargado de la parada ordenada
     * @param usageWindow          ventana de uso a mostrar
     */
    public TrayMenu(LifecycleCoordinator lifecycleCoordinator, UsageWindow usageWindow) {
        this.lifecycleCoordinator = lifecycleCoordinator;
        this.usageWindow = usageWindow;
    }

    /**
     * Devuelve el {@link PopupMenu} construido para asociarlo a un {@link TrayIcon}.
     *
     * @return el menú contextual
     */
    public PopupMenu getPopupMenu() {
        if (popupMenu == null) {
            popupMenu = buildPopupMenu();
        }
        return popupMenu;
    }

    private PopupMenu buildPopupMenu() {
        PopupMenu menu = new PopupMenu();

        MenuItem showUsageItem = new MenuItem(SHOW_USAGE_ITEM_LABEL);
        showUsageItem.addActionListener(new ShowUsageActionListener());
        menu.add(showUsageItem);

        MenuItem exitItem = new MenuItem(EXIT_ITEM_LABEL);
        exitItem.addActionListener(new ExitActionListener());
        menu.add(exitItem);

        return menu;
    }

    /**
     * Listener interno que muestra la ventana de uso.
     */
    private class ShowUsageActionListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent event) {
            usageWindow.show();
        }
    }

    /**
     * Listener interno que solicita el cierre ordenado de la aplicación.
     */
    private class ExitActionListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent event) {
            lifecycleCoordinator.requestShutdown();
        }
    }
}
