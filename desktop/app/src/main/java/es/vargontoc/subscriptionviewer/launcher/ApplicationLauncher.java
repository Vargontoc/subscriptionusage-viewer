package es.vargontoc.subscriptionviewer.launcher;

import es.vargontoc.subscriptionviewer.context.ApplicationContextConfig;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * Punto de entrada de la aplicación de escritorio.
 *
 * <p>Arranca el contexto de Spring Boot en modo non-web usando
 * {@link SpringApplicationBuilder} para un control explícito del tipo de
 * aplicación.</p>
 */
public class ApplicationLauncher {

    public static void main(String[] args) {
        new SpringApplicationBuilder(ApplicationContextConfig.class)
                .web(WebApplicationType.NONE)
                .run(args);
    }
}
