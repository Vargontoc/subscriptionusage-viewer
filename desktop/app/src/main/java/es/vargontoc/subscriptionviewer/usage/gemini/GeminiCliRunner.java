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
        this("agy", Duration.ofSeconds(60), DEFAULT_MAX_OUTPUT_BYTES);
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
