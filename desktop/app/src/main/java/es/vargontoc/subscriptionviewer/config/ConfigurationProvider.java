package es.vargontoc.subscriptionviewer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.nio.file.Path;
import java.time.Duration;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    private String openCodeApiKeyOrigin = "ninguno";

    public ConfigurationProvider(AppProperties appProperties, Environment environment) {
        this.appProperties = appProperties;
        this.environment = environment;
        loadOpenCodeProperties();
    }

    @PostConstruct
    public void logResolvedProperties() {
        log.info("Propiedad app.name resuelta: {}", getAppName());
        log.info("Propiedad app.usage-state-file resuelta: {}", getUsageStateFile());
        log.info("Propiedad app.usage-poll-interval resuelta: {}", getUsagePollInterval());
        log.info("Propiedad subscription-viewer.opencode.cycle-day resuelta: {}", getOpenCodeCycleDay());
        log.info("Propiedad subscription-viewer.opencode.default-cap-usd resuelta: {}", getOpenCodeDefaultCapUsd());
        log.info("Propiedad subscription-viewer.opencode.poll-interval-seconds resuelta: {}", getOpenCodePollInterval());
        log.info("Propiedad subscription-viewer.opencode.command-timeout-seconds resuelta: {}", getOpenCodeCommandTimeout());
        log.info("OpenCode: clave configurada: {}; origen: {}",
                hasOpenCodeApiKey() ? "sí" : "no", openCodeApiKeyOrigin);
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

    public String getOpenCodeUsageUrl() {
        String url = appProperties.getOpencode().getUsageUrl();
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("La propiedad subscription-viewer.opencode.usage-url no puede estar vacia");
        }
        return url;
    }

    public Duration getOpenCodeUsagePollInterval() {
        Duration interval = appProperties.getOpencode().getUsagePollInterval();
        if (interval == null || interval.isNegative() || interval.isZero()) {
            throw new IllegalStateException("El intervalo de OpenCode debe ser positivo");
        }
        return interval.compareTo(Duration.ofSeconds(10)) < 0
                ? Duration.ofSeconds(10) : interval;
    }

    public int getOpenCodeCycleDay() {
        int day = appProperties.getOpencode().getCycleDay();
        return day >= 1 && day <= 31 ? day : 1;
    }

    public BigDecimal getOpenCodeDefaultCapUsd() {
        BigDecimal cap = appProperties.getOpencode().getDefaultCapUsd();
        return cap == null || cap.signum() <= 0 ? BigDecimal.valueOf(60) : cap;
    }

    /** Intervalo fijo de refresco, nunca inferior a diez segundos. */
    public Duration getOpenCodePollInterval() {
        return getOpenCodeUsagePollInterval();
    }

    public Duration getOpenCodeCommandTimeout() {
        Duration timeout = appProperties.getOpencode().getCommandTimeout();
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalStateException("El timeout de OpenCode debe ser positivo");
        }
        return timeout;
    }

    public List<AppProperties.ModelCap> getOpenCodeModelCaps() {
        return Collections.unmodifiableList(appProperties.getOpencode().getModelCaps());
    }

    public BigDecimal getOpenCodeModelCap(String model) {
        return getOpenCodeModelCaps().stream()
                .filter(entry -> entry.model() != null && entry.model().equals(model))
                .map(AppProperties.ModelCap::capUsd)
                .filter(cap -> cap != null && cap.signum() > 0)
                .findFirst().orElse(getOpenCodeDefaultCapUsd());
    }

    public Duration getOpenCodeBackoffMaxInterval() {
        Duration interval = appProperties.getOpencode().getBackoffMaxInterval();
        if (interval == null || interval.isNegative() || interval.isZero()) {
            throw new IllegalStateException("El tope de backoff de OpenCode debe ser positivo");
        }
        return interval;
    }

    /**
     * Devuelve la clave encapsulada de OpenCode, o {@code null} si no está
     * configurada.
     */
    public OpenCodeApiKey getOpenCodeApiKey() {
        return appProperties.getOpencode().getApiKey();
    }

    /** Indica si la fuente de OpenCode puede autenticarse, sin exponer el secreto. */
    public boolean hasOpenCodeApiKey() {
        OpenCodeApiKey key = getOpenCodeApiKey();
        return key != null && key.value() != null && !key.value().isBlank();
    }

    /** Devuelve el origen de la clave para el estado de arranque, nunca su valor. */
    public String getOpenCodeApiKeyOrigin() {
        return openCodeApiKeyOrigin;
    }

    private void loadOpenCodeProperties() {
        AppProperties.OpenCodeProperties properties = appProperties.getOpencode();
        String usageUrl = environment.getProperty("subscription-viewer.opencode.usage-url");
        String pollInterval = environment.getProperty("subscription-viewer.opencode.usage-poll-interval");
        String backoffInterval = environment.getProperty("subscription-viewer.opencode.backoff-max-interval");
        if (usageUrl != null) {
            properties.setUsageUrl(usageUrl);
        }
        if (pollInterval != null) {
            properties.setUsagePollInterval(parseDuration(pollInterval));
        }
        if (backoffInterval != null) {
            properties.setBackoffMaxInterval(parseDuration(backoffInterval));
        }
        properties.setCycleDay(parseCycleDay(environment.getProperty("subscription-viewer.opencode.cycle-day")));
        String defaultCap = environment.getProperty("subscription-viewer.opencode.default-cap-usd");
        if (defaultCap != null && !defaultCap.isBlank()) {
            try {
                properties.setDefaultCapUsd(new BigDecimal(defaultCap.trim()));
            } catch (NumberFormatException ignored) {
                // Un valor inválido conserva el tope seguro por defecto.
            }
        }
        String timeout = environment.getProperty("subscription-viewer.opencode.command-timeout-seconds");
        if (timeout != null && !timeout.isBlank()) {
            properties.setCommandTimeout(parseDuration(timeout));
        }
        String intervalSeconds = environment.getProperty("subscription-viewer.opencode.poll-interval-seconds");
        if (intervalSeconds != null && !intervalSeconds.isBlank()) {
            properties.setUsagePollInterval(parseDuration(intervalSeconds));
        }
        loadModelCaps(properties);
        // No se usa environment.getProperty(...) para la primera lectura: las
        // variables de entorno tienen reglas de enlace relajado y podrían
        // invertir la precedencia requerida. Se inspecciona primero el valor
        // explícito de las fuentes de configuración (YAML/test properties).
        String fileApiKey = findExplicitApiKeyProperty();
        if (isConfiguredApiKey(fileApiKey)) {
            properties.setApiKey(fileApiKey.trim());
            openCodeApiKeyOrigin = "application.yml";
            return;
        }

        String environmentApiKey = environment.getProperty("OPENCODE_API_KEY");
        if (!isConfiguredApiKey(environmentApiKey)) {
            environmentApiKey = System.getenv("OPENCODE_API_KEY");
        }
        if (isConfiguredApiKey(environmentApiKey)) {
            properties.setApiKey(environmentApiKey.trim());
            openCodeApiKeyOrigin = "entorno";
        }
    }

    private int parseCycleDay(String value) {
        if (value == null || value.isBlank()) return 1;
        try {
            int day = Integer.parseInt(value.trim());
            return day >= 1 && day <= 31 ? day : 1;
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private void loadModelCaps(AppProperties.OpenCodeProperties properties) {
        List<AppProperties.ModelCap> caps = new ArrayList<>();
        for (int index = 0; ; index++) {
            String model = environment.getProperty("subscription-viewer.opencode.model-caps[" + index + "].model");
            String cap = environment.getProperty("subscription-viewer.opencode.model-caps[" + index + "].cap-usd");
            if (model == null && cap == null) break;
            BigDecimal numericCap = null;
            if (cap != null && !cap.isBlank()) {
                try { numericCap = new BigDecimal(cap.trim()); } catch (NumberFormatException ignored) { }
            }
            caps.add(new AppProperties.ModelCap(model, numericCap));
        }
        properties.setModelCaps(caps);
    }

    private String findExplicitApiKeyProperty() {
        if (!(environment instanceof ConfigurableEnvironment configurableEnvironment)) {
            return environment.getProperty("subscription-viewer.opencode.api-key");
        }
        for (PropertySource<?> source : configurableEnvironment.getPropertySources()) {
            // SystemEnvironmentPropertySource puede resolver nombres relajados
            // y por ello no representa el valor del YAML.
            if ("systemEnvironment".equals(source.getName())
                    || source.getName().toLowerCase().contains("systemenvironment")) {
                continue;
            }
            Object value = source.getProperty("subscription-viewer.opencode.api-key");
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }

    private boolean isConfiguredApiKey(String value) {
        return value != null && !value.isBlank()
                && !"<YOUR_OPENCODE_API_KEY>".equals(value.trim());
    }

    private Duration parseDuration(String value) {
        String normalized = value.trim().toLowerCase();
        if (normalized.matches("-?\\d+")) {
            return Duration.ofSeconds(Long.parseLong(normalized));
        }
        if (normalized.endsWith("ms")) {
            return Duration.ofMillis(Long.parseLong(normalized.substring(0, normalized.length() - 2)));
        }
        if (normalized.endsWith("s")) {
            return Duration.ofSeconds(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
        }
        if (normalized.endsWith("m")) {
            return Duration.ofMinutes(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
        }
        if (normalized.endsWith("h")) {
            return Duration.ofHours(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
        }
        return Duration.parse(value);
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
