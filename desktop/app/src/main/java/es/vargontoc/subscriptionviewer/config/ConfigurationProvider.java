package es.vargontoc.subscriptionviewer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Proveedor de configuración que expone las propiedades externalizadas
 * al resto de componentes de la aplicación.
 *
 * <p>Resuelve los valores desde {@link AppProperties} y el {@link Environment}
 * de Spring, aplicando los valores por defecto cuando no exista un fichero
 * externo.</p>
 */
@Component
public class ConfigurationProvider {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationProvider.class);

    private final AppProperties appProperties;
    private final Environment environment;

    public ConfigurationProvider(AppProperties appProperties, Environment environment) {
        this.appProperties = appProperties;
        this.environment = environment;
    }

    @PostConstruct
    public void logResolvedProperties() {
        log.info("Propiedad app.name resuelta: {}", getAppName());
    }

    /**
     * Devuelve el nombre configurado de la aplicación.
     *
     * @return el valor de {@code app.name}
     */
    public String getAppName() {
        return appProperties.getName();
    }

    /**
     * Devuelve la ruta configurada del icono de bandeja.
     *
     * @return el valor de {@code app.tray-icon-path}
     */
    public String getTrayIconPath() {
        return appProperties.getTrayIconPath();
    }

    /**
     * Resuelve una propiedad genérica del entorno de Spring.
     *
     * @param key clave de la propiedad
     * @return el valor de la propiedad, o {@code null} si no está definida
     */
    public String getProperty(String key) {
        return environment.getProperty(key);
    }
}
