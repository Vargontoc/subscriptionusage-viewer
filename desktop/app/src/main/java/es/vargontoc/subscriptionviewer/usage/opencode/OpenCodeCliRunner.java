 package es.vargontoc.subscriptionviewer.usage.opencode;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import es.vargontoc.subscriptionviewer.config.ConfigurationProvider;
import es.vargontoc.subscriptionviewer.usage.cli.AbstractCliRunner;

/** Ejecuta una consulta de estadísticas del CLI sin hacer visible sus fallos. */
@Component
public class OpenCodeCliRunner extends AbstractCliRunner {
    private static final String COMMAND = "opencode";
    /** El JSON de estadísticas no debe poder consumir memoria sin límite. */
    public static final long DEFAULT_MAX_OUTPUT_BYTES = 1024 * 1024;

    @Autowired
    public OpenCodeCliRunner(ConfigurationProvider configurationProvider) {
        this(COMMAND, configurationProvider.getOpenCodeCommandTimeout(), DEFAULT_MAX_OUTPUT_BYTES);
    }

    public OpenCodeCliRunner(Duration timeout) {
        this(COMMAND, timeout, DEFAULT_MAX_OUTPUT_BYTES);
    }

    /** Constructor útil para pruebas y para cambiar el límite, sin cambiar la invocación normal. */
    public OpenCodeCliRunner(Duration timeout, long maxOutputBytes) {
        this(COMMAND, timeout, maxOutputBytes);
    }

    /** Constructor para integrar un ejecutable de prueba sin modificar el PATH del proceso. */
    public OpenCodeCliRunner(String command, Duration timeout, long maxOutputBytes) {
        super(command, timeout, maxOutputBytes, "opencode-cli-stdout", "OpenCode");
    }

    /** Ejecuta session.stats con instantes Unix expresados en milisegundos. */
    public CliResult run(String fromUnixMillis, String toUnixMillis) {
        List<String> arguments = List.of("api", "experimental.session.stats", "--param", "from=" + fromUnixMillis,
                "--param", "to=" + toUnixMillis, "--param", "tools=none");
        return execute(arguments);
    }

    public CliResult run(Long fromMillis, Long toMillis) {
        return run(fromMillis.toString(), toMillis.toString());
    }

    public CliResult execute(String fromUnixMillis, String toUnixMillis) {
        return run(fromUnixMillis, toUnixMillis);
    }

    /** Ejecuta la consulta de la ruta del directorio de datos de OpenCode. */
    public CliResult runDataPath() {
        CliExecution execution = executeCli(List.of("debug", "paths", "data"));
        CliResult result = toResult(execution);
        if (result.isFailure()) {
            return result;
        }
        String output = result.output().trim();
        // La CLI devuelve una única línea. No aceptamos una salida vacía o
        // ambigua para evitar que un mensaje incidental se use como ruta.
        if (output.isEmpty() || output.lines().count() != 1) {
            return toResult(failedExecution("ruta-no-disponible"));
        }
        return new CliResult(true, output);
    }

    private CliResult execute(List<String> arguments) { return toResult(executeCli(arguments)); }

    int liveProcessCount() { return activeProcessCount(); }

    void destroyActiveProcesses() { destroyActiveCliProcesses(); }

    private CliResult toResult(CliExecution execution) {
        return execution.success() ? new CliResult(true, execution.output()) : CliResult.failure();
    }

    public record CliResult(boolean success, String text) {
        static CliResult failure() { return new CliResult(false, ""); }
        public boolean isSuccess() { return success; }
        public boolean isFailure() { return !success; }
        public String output() { return text; }
    }
}
