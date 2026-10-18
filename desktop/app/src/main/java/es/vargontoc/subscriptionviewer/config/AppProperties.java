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

    /**
     * Ruta del fichero de estado de uso generado por el hook de Claude Code.
     */
    private String usageStateFile = "~/.claude/statusline-state.json";

    /**
     * Intervalo entre lecturas del fichero de estado.
     */
    private Duration usagePollInterval = Duration.ofSeconds(10);

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

    public String getUsageStateFile() {
        return usageStateFile;
    }

    public void setUsageStateFile(String usageStateFile) {
        this.usageStateFile = usageStateFile;
    }

    public Duration getUsagePollInterval() {
        return usagePollInterval;
    }

    public void setUsagePollInterval(Duration usagePollInterval) {
        this.usagePollInterval = usagePollInterval;
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
