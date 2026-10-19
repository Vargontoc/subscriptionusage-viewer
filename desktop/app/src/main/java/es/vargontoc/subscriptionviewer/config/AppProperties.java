package es.vargontoc.subscriptionviewer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Propiedades externalizadas de la aplicación bajo el prefijo {@code app}.
 *
 * <p>Los valores por defecto se documentan en {@code application.yml} empaquetado
 * y pueden sobrescribirse mediante un fichero externo.</p>
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    public static final Duration DEFAULT_CLAUDE_USAGE_POLL_INTERVAL = Duration.ofSeconds(10);
    public static final Duration DEFAULT_OPENAI_USAGE_POLL_INTERVAL = Duration.ofSeconds(60);
    public static final Duration MIN_USAGE_POLL_INTERVAL = Duration.ofSeconds(10);

    /** Una entrada de la tabla de topes de OpenCode. */
    public record ModelCap(String model, BigDecimal capUsd) {
    }

    /**
     * Nombre visible de la aplicación.
     */
    private String name = "Subscription Usage Viewer";

    /**
     * Ruta del icono mostrado en la bandeja del sistema, relativa al classpath.
     */
    private String trayIconPath = "icons/app-icon.png";

    /** Propiedades de consulta del proveedor OpenCode. */
    private final OpenCodeProperties opencode = new OpenCodeProperties();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTrayIconPath() {
        return trayIconPath;
    }

    public void setTrayIconPath(String trayIconPath) {
        this.trayIconPath = trayIconPath;
    }

    /**
     * Compatibilidad binaria con fixtures antiguos; la ruta efectiva de Claude
     * se resuelve exclusivamente bajo {@code subscription-viewer.claude}.
     */
    @Deprecated
    public void setUsageStateFile(String ignored) {
        // La propiedad antigua ya no forma parte de la configuración efectiva.
    }

    public OpenCodeProperties getOpencode() {
        return opencode;
    }

    /** Configuración externalizada bajo {@code subscription-viewer.opencode}. */
    public static class OpenCodeProperties {
        private Duration usagePollInterval = Duration.ofSeconds(60);
        private int cycleDay = 1;
        private BigDecimal defaultCapUsd = BigDecimal.valueOf(60);
        private Duration commandTimeout = Duration.ofSeconds(30);
        private List<ModelCap> modelCaps = new ArrayList<>();

        public int getCycleDay() { return cycleDay; }
        public void setCycleDay(int cycleDay) { this.cycleDay = cycleDay; }
        public BigDecimal getDefaultCapUsd() { return defaultCapUsd; }
        public void setDefaultCapUsd(BigDecimal defaultCapUsd) { this.defaultCapUsd = defaultCapUsd; }
        public Duration getCommandTimeout() { return commandTimeout; }
        public void setCommandTimeout(Duration commandTimeout) { this.commandTimeout = commandTimeout; }
        public List<ModelCap> getModelCaps() { return modelCaps; }
        public void setModelCaps(List<ModelCap> modelCaps) {
            this.modelCaps = modelCaps == null ? new ArrayList<>() : new ArrayList<>(modelCaps);
        }

        public Duration getUsagePollInterval() {
            return usagePollInterval;
        }

        public void setUsagePollInterval(Duration usagePollInterval) {
            this.usagePollInterval = usagePollInterval;
        }

        @Override
        public String toString() {
            return "OpenCodeProperties[usagePollInterval=" + usagePollInterval + "]";
        }
    }
}
