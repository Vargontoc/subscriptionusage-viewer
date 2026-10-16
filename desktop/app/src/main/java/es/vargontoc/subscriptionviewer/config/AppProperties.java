package es.vargontoc.subscriptionviewer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Propiedades externalizadas de la aplicación bajo el prefijo {@code app}.
 *
 * <p>Los valores por defecto se documentan en {@code application.yml} empaquetado
 * y pueden sobrescribirse mediante un fichero externo.</p>
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

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
}
