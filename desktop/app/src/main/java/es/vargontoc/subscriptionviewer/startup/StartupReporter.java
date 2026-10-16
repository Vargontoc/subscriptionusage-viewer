package es.vargontoc.subscriptionviewer.startup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Componente que registra el mensaje de aplicación lista una vez finalizado el
 * arranque.
 *
 * <p>Para evitar falsos positivos, espera a recibir tanto el
 * {@link ApplicationReadyEvent} del contenedor Spring como el evento personalizado
 * {@link TrayReadyEvent} que emite el indicador de bandeja. Solo cuando ambos
 * hayan ocurrido se escribe el mensaje inequívoco de que la aplicación está
 * lista.</p>
 */
@Component
public class StartupReporter {

    private static final Logger log = LoggerFactory.getLogger(StartupReporter.class);

    private static final String READY_MESSAGE = "Aplicación lista";

    private final AtomicBoolean contextReady = new AtomicBoolean(false);
    private final AtomicBoolean trayReady = new AtomicBoolean(false);

    /**
     * Escucha el evento que indica que el contexto de Spring Boot ha completado
     * el arranque.
     *
     * @param event el evento de aplicación lista
     */
    @EventListener
    public void onApplicationReady(ApplicationReadyEvent event) {
        markReady(contextReady);
    }

    /**
     * Escucha el evento que indica que el icono de bandeja está disponible.
     *
     * @param event el evento de bandeja lista
     */
    @EventListener
    public void onTrayReady(TrayReadyEvent event) {
        markReady(trayReady);
    }

    private synchronized void markReady(AtomicBoolean flag) {
        if (flag.compareAndSet(false, true) && contextReady.get() && trayReady.get()) {
            log.info(READY_MESSAGE);
        }
    }
}
