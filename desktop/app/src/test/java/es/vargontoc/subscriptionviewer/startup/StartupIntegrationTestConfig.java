package es.vargontoc.subscriptionviewer.startup;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configuración de soporte para los tests de integración de arranque.
 *
 * <p>En entornos sin bandeja del sistema {@code TrayIndicator} no puede publicar
 * {@link TrayReadyEvent}. Este bean simula esa señal cuando el contexto Spring
 * emite {@link ApplicationReadyEvent}, permitiendo que {@link StartupReporter}
 * registre el mensaje de aplicación lista durante los tests automatizados.</p>
 */
@Configuration
@Profile("integration-test")
public class StartupIntegrationTestConfig {

    @Bean
    TrayReadySimulator trayReadySimulator(ApplicationEventPublisher publisher) {
        return new TrayReadySimulator(publisher);
    }

    static class TrayReadySimulator implements ApplicationListener<ApplicationReadyEvent> {

        private final ApplicationEventPublisher publisher;

        TrayReadySimulator(ApplicationEventPublisher publisher) {
            this.publisher = publisher;
        }

        @Override
        public void onApplicationEvent(ApplicationReadyEvent event) {
            publisher.publishEvent(new TrayReadyEvent(this));
        }
    }
}
