package es.vargontoc.subscriptionviewer.startup;

import org.springframework.context.ApplicationEvent;

/**
 * Evento de Spring que indica que el icono de la aplicación ha sido añadido
 * correctamente a la bandeja del sistema.
 *
 * <p>Se publica desde {@link es.vargontoc.subscriptionviewer.tray.TrayIndicator}
 * una vez que el {@link java.awt.TrayIcon} está disponible para el usuario.</p>
 */
public class TrayReadyEvent extends ApplicationEvent {

    public TrayReadyEvent(Object source) {
        super(source);
    }
}
