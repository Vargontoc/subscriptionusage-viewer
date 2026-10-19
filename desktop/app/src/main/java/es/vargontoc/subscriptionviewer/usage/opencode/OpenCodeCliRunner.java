 package es.vargontoc.subscriptionviewer.usage.opencode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import es.vargontoc.subscriptionviewer.config.ConfigurationProvider;

/** Ejecuta una consulta de estadísticas del CLI sin hacer visible sus fallos. */
@Component
public final class OpenCodeCliRunner {
    private static final Logger log = LoggerFactory.getLogger(OpenCodeCliRunner.class);
    private static final String COMMAND = "opencode";
    /** El JSON de estadísticas no debe poder consumir memoria sin límite. */
    public static final long DEFAULT_MAX_OUTPUT_BYTES = 1024 * 1024;

    private final String command;
    private final Duration timeout;
    private final long maxOutputBytes;
    private final Set<Process> activeProcesses = ConcurrentHashMap.newKeySet();

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
        if (command == null || command.isBlank() || timeout == null || timeout.isZero() || timeout.isNegative()
                || maxOutputBytes <= 0) {
            throw new IllegalArgumentException("Configuración inválida del runner de OpenCode");
        }
        this.command = command;
        this.timeout = timeout;
        this.maxOutputBytes = maxOutputBytes;
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
        CliResult result = execute(List.of("debug", "paths", "data"));
        if (result.isFailure()) {
            return result;
        }
        String output = result.output().trim();
        // La CLI devuelve una única línea. No aceptamos una salida vacía o
        // ambigua para evitar que un mensaje incidental se use como ruta.
        if (output.isEmpty() || output.lines().count() != 1) {
            return failure("ruta-no-disponible");
        }
        return new CliResult(true, output);
    }

    private CliResult execute(List<String> arguments) {
        Process process = null;
        ExecutorService readerExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "opencode-cli-stdout");
            thread.setDaemon(true);
            return thread;
        });
        
        try {
            process = new ProcessBuilder(processArguments(arguments))
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            activeProcesses.add(process);
            process.getOutputStream().close();
            Process runningProcess = process;
            Future<ReadResult> stdout = readerExecutor.submit(() -> readStdout(runningProcess.getInputStream()));

            long deadline = System.nanoTime() + timeout.toNanos();
            while (process.isAlive() && !stdout.isDone()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    destroy(process);
                    return failure("timeout");
                }
                process.waitFor(Math.min(TimeUnit.NANOSECONDS.toMillis(remaining), 50), TimeUnit.MILLISECONDS);
            }
            if (process.isAlive()) {
                destroy(process);
                return failure("salida-excesiva");
            }
            int exitCode = process.exitValue();
            ReadResult output = stdout.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            if (!output.success() || exitCode != 0) {
                return failure(output.success() ? "codigo-salida" : "salida-excesiva");
            }
            return new CliResult(true, output.text());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (process != null) destroy(process);
            return failure("interrumpido");
        } catch (IOException | ExecutionException | java.util.concurrent.TimeoutException | RuntimeException exception) {
            if (process != null) destroy(process);
            return failure("excepcion");
        } finally {
            if (process != null) activeProcesses.remove(process);
            readerExecutor.shutdownNow();
        }
    }

    int liveProcessCount() {
        activeProcesses.removeIf(process -> !process.isAlive());
        return activeProcesses.size();
    }

    void destroyActiveProcesses() {
        activeProcesses.forEach(this::destroy);
        activeProcesses.clear();
    }

    private List<String> processArguments(List<String> arguments) {
        List<String> commandLine = new ArrayList<>();
        if (isWindows()) {
            commandLine.add("cmd.exe");
            commandLine.add("/d");
            commandLine.add("/c");
            StringBuilder commandString = new StringBuilder().append(command);
            for (String argument : arguments) {
                commandString.append(' ').append(argument);
            }
            commandLine.add(commandString.toString());
        } else {
            commandLine.add(command);
            commandLine.addAll(arguments);
        }
        return commandLine;
    }

    // "cmd.exe /d /c opencode api experimental.session.stats --param from=1792368000000 --param to=1792454400000 --param tools=none"

    private ReadResult readStdout(InputStream input) {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            long total = 0;
            while ((count = stream.read(buffer)) != -1) {
                total += count;
                if (total > maxOutputBytes) return ReadResult.failure();
                output.write(buffer, 0, count);
            }
            return ReadResult.success(output.toString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            return ReadResult.failure();
        }
    }

    private void destroy(Process process) {
        process.toHandle().descendants().forEach(handle -> {
            if (handle.isAlive()) handle.destroyForcibly();
        });
        if (process.isAlive()) process.destroyForcibly();
    }

    private CliResult failure(String category) {
        // Deliberadamente no se registra la excepción, los argumentos ni ninguna salida del proceso.
        log.debug("Fallo de consulta del CLI de OpenCode: {}", category);
        return CliResult.failure();
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private record ReadResult(boolean success, String text) {
        static ReadResult success(String text) { return new ReadResult(true, text); }
        static ReadResult failure() { return new ReadResult(false, ""); }
    }

    public record CliResult(boolean success, String text) {
        static CliResult failure() { return new CliResult(false, ""); }
        public boolean isSuccess() { return success; }
        public boolean isFailure() { return !success; }
        public String output() { return text; }
    }
}
