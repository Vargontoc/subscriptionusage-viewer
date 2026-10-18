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
        /** Compatibilidad temporal con la configuración de v1; se retirará en TASK-021. */
        private String usageUrl = "https://opencode.ai/zen/go/v1/usage";
        private Duration usagePollInterval = Duration.ofSeconds(60);
        private int cycleDay = 1;
        private BigDecimal defaultCapUsd = BigDecimal.valueOf(60);
        private Duration commandTimeout = Duration.ofSeconds(30);
        private List<ModelCap> modelCaps = new ArrayList<>();
        /** Compatibilidad temporal con la configuración de v1; se retirará en TASK-021. */
        private Duration backoffMaxInterval = Duration.ofSeconds(300);
        /** Compatibilidad temporal con la configuración de v1; se retirará en TASK-021. */
        private OpenCodeApiKey apiKey;

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

        public String getUsageUrl() {
            return usageUrl;
        }

        public void setUsageUrl(String usageUrl) {
            this.usageUrl = usageUrl;
        }

        public Duration getUsagePollInterval() {
            return usagePollInterval;
        }

        public void setUsagePollInterval(Duration usagePollInterval) {
            this.usagePollInterval = usagePollInterval;
        }

        public Duration getBackoffMaxInterval() {
            return backoffMaxInterval;
        }

        public void setBackoffMaxInterval(Duration backoffMaxInterval) {
            this.backoffMaxInterval = backoffMaxInterval;
        }

        public OpenCodeApiKey getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = new OpenCodeApiKey(apiKey);
        }

        /**
         * No incluye la clave, ni siquiera su representación enmascarada,
         * para evitar que un volcado de propiedades la trate como dato visible.
         */
        @Override
        public String toString() {
            return "OpenCodeProperties[usageUrl=" + usageUrl
                    + ", usagePollInterval=" + usagePollInterval
                    + ", backoffMaxInterval=" + backoffMaxInterval + "]";
        }
    }
}
