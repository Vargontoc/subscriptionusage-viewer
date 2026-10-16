package es.vargontoc.subscriptionviewer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.nio.file.Path;
import java.time.Duration;

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
        log.info("Propiedad app.usage-state-file resuelta: {}", getUsageStateFile());
        log.info("Propiedad app.usage-poll-interval resuelta: {}", getUsagePollInterval());
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
     * Devuelve la ruta del fichero de estado de uso, expandiendo {@code ~}
     * por el directorio de usuario.
     *
     * @return {@code Path} absoluto del fichero de estado
     * @throws IllegalStateException si la propiedad no está configurada
     */
    public Path getUsageStateFile() {
        String file = appProperties.getUsageStateFile();
        if (file == null || file.isBlank()) {
            throw new IllegalStateException("La propiedad app.usage-state-file no puede estar vacia");
        }
        return Path.of(expandTilde(file)).toAbsolutePath().normalize();
    }

    /**
     * Devuelve el intervalo entre lecturas del fichero de estado.
     *
     * @return {@code Duration} positivo
     * @throws IllegalStateException si el intervalo no es positivo
     */
    public Duration getUsagePollInterval() {
        Duration interval = appProperties.getUsagePollInterval();
        if (interval == null || interval.isNegative() || interval.isZero()) {
            throw new IllegalStateException("La propiedad app.usage-poll-interval debe ser positiva");
        }
        return interval;
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

    private String expandTilde(String path) {
        if (path.startsWith("~/")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        if ("~".equals(path)) {
            return System.getProperty("user.home");
        }
        return path;
    }
}
