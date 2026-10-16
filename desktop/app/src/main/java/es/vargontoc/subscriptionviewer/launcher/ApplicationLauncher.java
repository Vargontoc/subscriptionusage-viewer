package es.vargontoc.subscriptionviewer.launcher;

import es.vargontoc.subscriptionviewer.context.ApplicationContextConfig;
import es.vargontoc.subscriptionviewer.lifecycle.LifecycleCoordinator;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Punto de entrada de la aplicación de escritorio.
 *
 * <p>Arranca el contexto de Spring Boot en modo non-web usando
 * {@link SpringApplicationBuilder} y, una vez completado el arranque, delega en
 * {@link LifecycleCoordinator} para mantener el proceso vivo hasta que se
 * solicite el cierre. Al retornar de la espera, cierra el contexto de forma
 * ordenada.</p>
 *
 * <p>Se registra un {@link Runtime#addShutdownHook(Thread) shutdown hook} que
 * cierra el contexto si la JVM recibe una señal del sistema, garantizando la
 * liberación de recursos gestionados sin recurrir a {@code System.exit}.</p>
 */
public class ApplicationLauncher {

    public static void main(String[] args) throws InterruptedException {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(ApplicationContextConfig.class)
                .web(WebApplicationType.NONE)
                // Spring Boot activa java.awt.headless por defecto, lo que
                // deshabilita la bandeja del sistema y las ventanas Swing.
                .headless(false)
                .run(args);

        Thread shutdownHook = new Thread(() -> closeContext(context));
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        LifecycleCoordinator coordinator = context.getBean(LifecycleCoordinator.class);
        try {
            coordinator.waitForShutdown();
        } finally {
            closeContext(context);
        }
    }

    private static void closeContext(ConfigurableApplicationContext context) {
        if (context.isActive()) {
            context.close();
        }
    }
}
