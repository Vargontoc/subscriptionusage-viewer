package es.vargontoc.subscriptionviewer.config;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
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
    private String claudeUsageStateFile = "~/.claude/statusline-dump.json";
    private Duration claudeUsagePollInterval = AppProperties.DEFAULT_CLAUDE_USAGE_POLL_INTERVAL;
    private Duration openAiUsagePollInterval = AppProperties.DEFAULT_OPENAI_USAGE_POLL_INTERVAL;
    private Duration geminiUsagePollInterval = AppProperties.DEFAULT_GEMINI_USAGE_POLL_INTERVAL;

    public ConfigurationProvider(AppProperties appProperties, Environment environment) {
        this.appProperties = appProperties;
        this.environment = environment;
        loadClaudeAndOpenAiProperties();
        loadOpenCodeProperties();
        loadGeminiProperties();
    }

    @PostConstruct
    public void logResolvedProperties() {
        log.info("Propiedad app.name resuelta: {}", getAppName());
        log.info("Propiedad subscription-viewer.claude.usage-state-file resuelta: {}", getUsageStateFile());
        log.info("Propiedad subscription-viewer.claude.usage-poll-interval resuelta: {}", getUsagePollInterval());
        log.info("Propiedad subscription-viewer.openai.usage-poll-interval resuelta: {}", getOpenAiUsagePollInterval());
        log.info("Propiedad subscription-viewer.gemini.usage-poll-interval resuelta: {}", getGeminiUsagePollInterval());
        log.info("Propiedad subscription-viewer.opencode.cycle-day resuelta: {}", getOpenCodeCycleDay());
        log.info("Propiedad subscription-viewer.opencode.default-cap-usd resuelta: {}", getOpenCodeDefaultCapUsd());
        log.info("Propiedad subscription-viewer.opencode.poll-interval-seconds resuelta: {}", getOpenCodePollInterval());
        log.info("Propiedad subscription-viewer.opencode.command-timeout-seconds resuelta: {}", getOpenCodeCommandTimeout());
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
        String file = claudeUsageStateFile;
        if (file == null || file.isBlank()) {
            throw new IllegalStateException("La propiedad subscription-viewer.claude.usage-state-file no puede estar vacia");
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
        Duration interval = claudeUsagePollInterval;
        if (interval == null || interval.isNegative() || interval.isZero()) {
            throw new IllegalStateException("La propiedad subscription-viewer.claude.usage-poll-interval debe ser positiva");
        }
        return interval;
    }

    /** Devuelve el intervalo configurado para las consultas de OpenAI. */
    public Duration getOpenAiUsagePollInterval() {
        return enforceMinimum(openAiUsagePollInterval,
                "subscription-viewer.openai.usage-poll-interval");
    }

    /** Devuelve el intervalo configurado para las consultas de Gemini. */
    public Duration getGeminiUsagePollInterval() {
        return enforceMinimum(geminiUsagePollInterval,
                "subscription-viewer.gemini.usage-poll-interval",
                AppProperties.MIN_GEMINI_USAGE_POLL_INTERVAL);
    }

    /**
     * Indica si la sección de OpenCode está declarada en la configuración
     * efectiva. No se consulta {@link AppProperties#getOpencode()}, ya que esa
     * subsección se instancia siempre aunque no exista en la configuración.
     */
    public boolean isOpenCodeConfigured() {
        String prefix = "subscription-viewer.opencode";
        if (environment.containsProperty(prefix)) {
            return true;
        }
        if (environment instanceof ConfigurableEnvironment configurableEnvironment) {
            for (org.springframework.core.env.PropertySource<?> source
                    : configurableEnvironment.getPropertySources()) {
                if (source instanceof EnumerablePropertySource<?> enumerable) {
                    for (String propertyName : enumerable.getPropertyNames()) {
                        if (propertyName.equals(prefix) || propertyName.startsWith(prefix + ".")) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public Duration getOpenCodeUsagePollInterval() {
        Duration interval = appProperties.getOpencode().getUsagePollInterval();
        return enforceMinimum(interval, "subscription-viewer.opencode.usage-poll-interval");
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
                .map(e -> e.capUsd())
                .filter(cap -> cap != null && cap.signum() > 0)
                .findFirst().orElse(getOpenCodeDefaultCapUsd());
    }

    private void loadOpenCodeProperties() {
        AppProperties.OpenCodeProperties properties = appProperties.getOpencode();
        String pollInterval = environment.getProperty("subscription-viewer.opencode.usage-poll-interval");
        boolean hasUsagePollInterval = pollInterval != null && !pollInterval.isBlank();
        if (pollInterval != null) {
            if (hasUsagePollInterval) {
                properties.setUsagePollInterval(parsePositiveDuration(pollInterval,
                        "subscription-viewer.opencode.usage-poll-interval"));
            }
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
            properties.setCommandTimeout(parsePositiveDuration(timeout,
                    "subscription-viewer.opencode.command-timeout-seconds"));
        }
        String intervalSeconds = environment.getProperty("subscription-viewer.opencode.poll-interval-seconds");
        if (!hasUsagePollInterval && intervalSeconds != null && !intervalSeconds.isBlank()) {
            properties.setUsagePollInterval(parsePositiveDuration(intervalSeconds,
                    "subscription-viewer.opencode.poll-interval-seconds"));
        }
        loadModelCaps(properties);
    }

    private void loadClaudeAndOpenAiProperties() {
        String claudeFile = environment.getProperty("subscription-viewer.claude.usage-state-file");
        if (claudeFile != null) {
            claudeUsageStateFile = claudeFile;
        }
        String claudeInterval = environment.getProperty("subscription-viewer.claude.usage-poll-interval");
        if (claudeInterval != null && !claudeInterval.isBlank()) {
            claudeUsagePollInterval = parsePositiveDuration(claudeInterval,
                    "subscription-viewer.claude.usage-poll-interval");
        }
        String openAiInterval = environment.getProperty("subscription-viewer.openai.usage-poll-interval");
        if (openAiInterval != null && !openAiInterval.isBlank()) {
            openAiUsagePollInterval = parsePositiveDuration(openAiInterval,
                    "subscription-viewer.openai.usage-poll-interval");
        }
    }

    private void loadGeminiProperties() {
        String interval = environment.getProperty("subscription-viewer.gemini.usage-poll-interval");
        if (interval != null && !interval.isBlank()) {
            geminiUsagePollInterval = parsePositiveDuration(interval,
                    "subscription-viewer.gemini.usage-poll-interval");
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

    private Duration parsePositiveDuration(String value, String propertyName) {
        try {
            String normalized = value.trim().toLowerCase();
            Duration duration;
            if (normalized.matches("-?\\d+")) {
                duration = Duration.ofSeconds(Long.parseLong(normalized));
            } else if (normalized.endsWith("ms")) {
                duration = Duration.ofMillis(Long.parseLong(normalized.substring(0, normalized.length() - 2)));
            } else if (normalized.endsWith("s")) {
                duration = Duration.ofSeconds(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
            } else if (normalized.endsWith("m")) {
                duration = Duration.ofMinutes(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
            } else if (normalized.endsWith("h")) {
                duration = Duration.ofHours(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
            } else {
                duration = Duration.parse(value.trim());
            }
            if (duration.isZero() || duration.isNegative()) {
                throw invalidDuration(propertyName, null);
            }
            return duration;
        } catch (NumberFormatException | DateTimeParseException | ArithmeticException ex) {
            throw invalidDuration(propertyName, ex);
        }
    }

    private IllegalStateException invalidDuration(String propertyName, Exception cause) {
        return new IllegalStateException("Error de configuración en la propiedad " + propertyName
                + ": debe ser una duración válida y positiva", cause);
    }

    private Duration enforceMinimum(Duration interval, String propertyName) {
        return enforceMinimum(interval, propertyName, AppProperties.MIN_USAGE_POLL_INTERVAL);
    }

    private Duration enforceMinimum(Duration interval, String propertyName, Duration minimum) {
        if (interval == null || interval.isZero() || interval.isNegative()) {
            throw invalidDuration(propertyName, null);
        }
        return interval.compareTo(minimum) < 0 ? minimum : interval;
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
