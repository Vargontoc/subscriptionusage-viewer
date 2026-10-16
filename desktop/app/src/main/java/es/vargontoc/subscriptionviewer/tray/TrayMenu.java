package es.vargontoc.subscriptionviewer.tray;

import es.vargontoc.subscriptionviewer.lifecycle.LifecycleCoordinator;
import org.springframework.stereotype.Component;

import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Menú contextual asociado al icono de bandeja del sistema.
 *
 * <p>Construye un {@link PopupMenu} de AWT con, al menos, una opción de cierre.
 * Al activarse dicha opción, delega en {@link LifecycleCoordinator#requestShutdown()}
 * para iniciar el apagado ordenado de la aplicación, manteniendo la estrategia
 * de cierre centralizada en el coordinador de ciclo de vida.</p>
 */
@Component
public class TrayMenu {

    private static final String EXIT_ITEM_LABEL = "Cerrar";

    private final LifecycleCoordinator lifecycleCoordinator;
    private PopupMenu popupMenu;

    /**
     * Crea el menú contextual con la opción de cierre.
     *
     * <p>Los componentes AWT se construyen de forma diferida al solicitar el
     * {@link PopupMenu}, de modo que el bean pueda instanciarse incluso en
     * entornos headless donde no se vaya a mostrar la bandeja.</p>
     *
     * @param lifecycleCoordinator coordinador encargado de la parada ordenada
     */
    public TrayMenu(LifecycleCoordinator lifecycleCoordinator) {
        this.lifecycleCoordinator = lifecycleCoordinator;
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

        MenuItem exitItem = new MenuItem(EXIT_ITEM_LABEL);
        exitItem.addActionListener(new ExitActionListener());
        menu.add(exitItem);

        return menu;
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
