package es.vargontoc.subscriptionviewer.usage.gemini;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import es.vargontoc.subscriptionviewer.usage.cli.AbstractCliRunner;

/** Ejecuta la consulta de uso de Gemini mediante el CLI agy. */
@Component
public class GeminiCliRunner extends AbstractCliRunner {
    public static final long DEFAULT_MAX_OUTPUT_BYTES = 1024 * 1024;

    /** Constructor por defecto para Spring. */
    @Autowired
    public GeminiCliRunner() {
        this(resolveDefaultCommand(), Duration.ofSeconds(60), DEFAULT_MAX_OUTPUT_BYTES);
    }

    /** Localiza agy con {@code where}; si el PATH del proceso no lo incluye, prueba la ruta de instalación habitual. */
    private static String resolveDefaultCommand() {
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            try {
                Process where = new ProcessBuilder("where.exe", "agy")
                        .redirectError(ProcessBuilder.Redirect.DISCARD).start();
                where.getOutputStream().close();
                String found = null;
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(where.getInputStream()))) {
                    found = reader.readLine();
                }
                if (where.waitFor(5, java.util.concurrent.TimeUnit.SECONDS) && where.exitValue() == 0
                        && found != null && !found.isBlank()) {
                    return found.trim();
                }
                where.destroyForcibly();
            } catch (java.io.IOException | InterruptedException exception) {
                if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            }
        }
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null) {
            java.nio.file.Path installed = java.nio.file.Path.of(localAppData, "agy", "bin", "agy.exe");
            if (java.nio.file.Files.isRegularFile(installed)) {
                return installed.toString();
            }
        }
        return "agy";
    }

    /** Constructor para pruebas con un ejecutable y límites controlados. */
    public GeminiCliRunner(String command, Duration timeout, long maxOutputBytes) {
        super(command, timeout, maxOutputBytes, "gemini-cli-stdout", "Gemini");
    }

    /** Ejecuta la consulta de uso con argumentos fijos, sin entrada del usuario. */
    public CliResult run() {
        List<String> arguments = List.of("-p", "/usage", "--output-format", "json");
        return toResult(executeCli(arguments));
    }

    int liveProcessCount() {
        return activeProcessCount();
    }

    void destroyActiveProcesses() {
        destroyActiveCliProcesses();
    }

    private CliResult toResult(CliExecution execution) {
        return execution.success() ? new CliResult(true, execution.output()) : CliResult.failure();
    }

    public record CliResult(boolean success, String text) {
        static CliResult failure() {
            return new CliResult(false, "");
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean isFailure() {
            return !success;
        }

        public String output() {
            return text;
        }
    }
}
