package es.vargontoc.subscriptionviewer.context;

import es.vargontoc.subscriptionviewer.config.AppProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

/**
 * Clase de configuración raíz del contexto de Spring Boot.
 *
 * <p>Activa el escaneo de componentes del módulo y registra las propiedades
 * externalizadas de la aplicación y el {@link ObjectMapper} compartido.</p>
 */
@Configuration
@ComponentScan(basePackages = "es.vargontoc.subscriptionviewer")
@EnableConfigurationProperties(AppProperties.class)
public class ApplicationContextConfig {

    /**
     * ObjectMapper de Jackson disponible para la aplicación.
     *
     * @return object mapper por defecto
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    /**
     * Reloj del sistema para que los componentes puedan calcular tiempos de
     * forma testable.
     *
     * @return reloj por defecto del sistema
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
