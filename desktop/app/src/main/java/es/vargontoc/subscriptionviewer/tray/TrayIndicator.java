package es.vargontoc.subscriptionviewer.tray;

import es.vargontoc.subscriptionviewer.config.ConfigurationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.awt.AWTException;
import java.awt.GraphicsEnvironment;
import java.awt.SystemTray;
import java.awt.TrayIcon;

/**
 * Indicador visual de la aplicación en la bandeja del sistema.
 *
 * <p>Implementa {@link SmartLifecycle} para añadir el icono a la bandeja una
 * vez que el contexto de Spring ha completado el arranque. Si la bandeja no
 * está disponible, registra un aviso y permite que la aplicación continúe sin
 * abortar.</p>
 *
 * <p>Registra un {@link Runtime#addShutdownHook(Thread) shutdown hook} que
 * libera el icono en caso de cierre abrupto de la JVM, y expone
 * {@link #removeIcon()} para que el coordinador de ciclo de vida lo invoque
 * durante un cierre ordenado.</p>
 */
@Component
public class TrayIndicator implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TrayIndicator.class);

    private final ConfigurationProvider configurationProvider;
    private final TrayIconFactory trayIconFactory;
    private final TrayMenu trayMenu;

    private volatile boolean running = false;
    private volatile TrayIcon trayIcon;

    public TrayIndicator(ConfigurationProvider configurationProvider,
                         TrayIconFactory trayIconFactory,
                         TrayMenu trayMenu) {
        this.configurationProvider = configurationProvider;
        this.trayIconFactory = trayIconFactory;
        this.trayMenu = trayMenu;
    }

    @Override
    public void start() {
        if (!isSystemTraySupported()) {
            log.warn("La bandeja del sistema no está disponible; el icono no se mostrará.");
            return;
        }

        try {
            String tooltip = configurationProvider.getAppName();
            String iconPath = configurationProvider.getTrayIconPath();
            this.trayIcon = trayIconFactory.createTrayIcon(tooltip, iconPath);
            this.trayIcon.setPopupMenu(trayMenu.getPopupMenu());

            SystemTray.getSystemTray().add(trayIcon);
            registerShutdownHook();

            log.info("Icono añadido a la bandeja del sistema.");
            this.running = true;
        } catch (IllegalStateException e) {
            log.error("No se ha podido cargar el icono de bandeja; el indicador no se mostrará.", e);
        } catch (AWTException e) {
            log.error("No se ha podido añadir el icono a la bandeja del sistema.", e);
        }
    }

    /**
     * Registra un shutdown hook que intenta liberar el icono en cierres abruptos.
     *
     * <p>La operación de {@link SystemTray#remove(TrayIcon)} puede bloquear en
     * algunos entornos (por ejemplo, Windows) si se ejecuta directamente en un
     * shutdown hook. Para evitar que la JVM no finalice, la eliminación se
     * delega a un hilo daemon con un tiempo de espera máximo.</p>
     */
    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Thread remover = new Thread(this::removeIcon);
            remover.setDaemon(true);
            remover.start();
            try {
                remover.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
    }

    @Override
    public void stop() {
        removeIcon();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * Elimina el icono de la bandeja del sistema si estaba registrado.
     *
     * <p>Es seguro invocar este método varias veces; tras la primera llamada
     * las siguientes no tienen efecto.</p>
     */
    public void removeIcon() {
        TrayIcon current = this.trayIcon;
        if (current != null) {
            SystemTray.getSystemTray().remove(current);
            this.trayIcon = null;
            log.debug("Icono eliminado de la bandeja del sistema.");
        }
    }

    /**
     * Devuelve el icono actual de bandeja, o {@code null} si no se ha
     * inicializado.
     *
     * <p>Visibilidad de paquete para facilitar las pruebas.</p>
     *
     * @return el {@link TrayIcon} registrado, o {@code null}
     */
    TrayIcon getTrayIcon() {
        return trayIcon;
    }

    /**
     * Indica si la bandeja del sistema está soportada en este entorno.
     *
     * <p>Se extrae como método de instancia para permitir su sustitución en
     * pruebas unitarias.</p>
     *
     * @return {@code true} si {@link SystemTray#isSupported()} es {@code true}
     */
    boolean isSystemTraySupported() {
        return !GraphicsEnvironment.isHeadless() && SystemTray.isSupported();
    }
}
