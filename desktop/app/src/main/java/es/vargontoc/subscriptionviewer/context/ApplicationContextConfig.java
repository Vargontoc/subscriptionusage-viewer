package es.vargontoc.subscriptionviewer.context;

import es.vargontoc.subscriptionviewer.config.AppProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Clase de configuración raíz del contexto de Spring Boot.
 *
 * <p>Activa el escaneo de componentes del módulo y registra las propiedades
 * externalizadas de la aplicación.</p>
 */
@Configuration
@ComponentScan(basePackages = "es.vargontoc.subscriptionviewer")
@EnableConfigurationProperties(AppProperties.class)
public class ApplicationContextConfig {
}
