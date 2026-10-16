package es.vargontoc.subscriptionviewer.tray;

import org.springframework.stereotype.Component;

import java.awt.Image;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.net.URL;

/**
 * Fábrica de {@link TrayIcon} para el indicador de bandeja del sistema.
 *
 * <p>Encapsula la carga de la imagen desde el classpath y la creación del
 * {@link TrayIcon} configurado con el tooltip correspondiente.</p>
 */
@Component
public class TrayIconFactory {

    /**
     * Crea un {@link TrayIcon} con la imagen y el tooltip indicados.
     *
     * @param tooltip  texto que se mostrará al situar el cursor sobre el icono
     * @param iconPath ruta del recurso de imagen, relativa al classpath
     * @return un {@link TrayIcon} listo para añadir a la bandeja
     * @throws IllegalStateException si no se encuentra el recurso de imagen
     */
    public TrayIcon createTrayIcon(String tooltip, String iconPath) {
        URL resource = getClass().getClassLoader().getResource(iconPath);
        if (resource == null) {
            throw new IllegalStateException("No se ha encontrado el recurso de icono: " + iconPath);
        }

        Image image = Toolkit.getDefaultToolkit().getImage(resource);
        TrayIcon trayIcon = new TrayIcon(image, tooltip);
        trayIcon.setImageAutoSize(true);
        return trayIcon;
    }
}
