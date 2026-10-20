package es.vargontoc.subscriptionviewer.usage.cli;

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

/** Mecánica común para ejecutar comandos CLI sin exponer sus detalles de error. */
public abstract class AbstractCliRunner {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final String command;
    private final Duration timeout;
    private final long maxOutputBytes;
    private final String readerThreadName;
    private final String logLabel;
    private final Set<Process> activeProcesses = ConcurrentHashMap.newKeySet();

    protected AbstractCliRunner(String command, Duration timeout, long maxOutputBytes,
                                String readerThreadName, String logLabel) {
        if (command == null || command.isBlank() || timeout == null || timeout.isZero() || timeout.isNegative()
                || maxOutputBytes <= 0 || readerThreadName == null || readerThreadName.isBlank()
                || logLabel == null || logLabel.isBlank()) {
            throw new IllegalArgumentException("Configuración inválida del runner CLI");
        }
        this.command = command;
        this.timeout = timeout;
        this.maxOutputBytes = maxOutputBytes;
        this.readerThreadName = readerThreadName;
        this.logLabel = logLabel;
    }

    /** Ejecuta los argumentos y conserva también el código de salida para la subclase. */
    protected final CliExecution executeCli(List<String> arguments) {
    
        Process process = null;
        ExecutorService readerExecutor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, readerThreadName);
            thread.setDaemon(true);
            return thread;
        });
        try {
            process = new ProcessBuilder(processArguments(arguments))
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            activeProcesses.add(process);
            process.getOutputStream().close();
            Process runningProcess = process;
            Future<ReadResult> stdout = readerExecutor.submit(() -> readStdout(runningProcess.getInputStream()));
            long deadline = System.nanoTime() + timeout.toNanos();

            while (process.isAlive()) {
                if (stdout.isDone()) {
                    ReadResult completed = stdout.get();
                    if (!completed.success()) {
                        destroy(process);
                        return failure("salida-excesiva", null);
                    }
                }
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    destroy(process);
                    return failure("timeout", null);
                }
                process.waitFor(Math.max(1, Math.min(TimeUnit.NANOSECONDS.toMillis(remaining), 50)),
                        TimeUnit.MILLISECONDS);
            }

            long remaining = deadline - System.nanoTime();
            if (!stdout.isDone()) {
                if (remaining <= 0) {
                    destroy(process);
                    return failure("salida-excesiva", process.exitValue());
                }
                try {
                    stdout.get(remaining, TimeUnit.NANOSECONDS);
                } catch (java.util.concurrent.TimeoutException exception) {
                    return failure("salida-excesiva", process.exitValue());
                }
            }
            ReadResult output = stdout.get();
            int exitCode = process.exitValue();
            if (!output.success()) {
                return failure("salida-excesiva", exitCode);
            }
            if (exitCode != 0) {
                log.debug("Fallo de consulta del CLI de {}: codigo-salida", logLabel);
                return new CliExecution(false, output.text(), exitCode);
            }
            return new CliExecution(true, output.text(), exitCode);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (process != null) destroy(process);
            return failure("interrumpido", null);
        } catch (IOException | ExecutionException | RuntimeException exception) {
            if (process != null) destroy(process);
            return failure("excepcion", null);
        } finally {
            if (process != null) activeProcesses.remove(process);            readerExecutor.shutdownNow();
        }
    }

    /** Accesos de ciclo de vida conservados para los runners concretos. */
    protected final int activeProcessCount() {
        activeProcesses.removeIf(process -> !process.isAlive());
        return activeProcesses.size();
    }

    protected final void destroyActiveCliProcesses() {
        activeProcesses.forEach(this::destroy);
        activeProcesses.clear();
    }

    /** Registra una categoría interna para validaciones propias de una subclase. */
    protected final CliExecution failedExecution(String category) {
        return failure(category, null);
    }

    private CliExecution failure(String category, Integer exitCode) {
        // No se registran salida, argumentos, código ni excepciones: solo la categoría.
        log.debug("Fallo de consulta del CLI de {}: {}", logLabel, category);
        return new CliExecution(false, "", exitCode);
    }

    private List<String> processArguments(List<String> arguments) {
        List<String> commandLine = new ArrayList<>();
        boolean directExecutable = command.toLowerCase().endsWith(".exe");
        if (isWindows() && !directExecutable) {
            commandLine.add("cmd.exe");
            commandLine.add("/d");
            commandLine.add("/c");
            // Cada token va como argumento propio: Java solo entrecomilla los que llevan espacios,
            // y cmd.exe resuelve agy.cmd/agy.exe por PATH sin que las comillas de Java se mezclen
            // con las nuestras.
            commandLine.add(command);
            commandLine.addAll(arguments);
        } else {
            commandLine.add(command);
            commandLine.addAll(arguments);
        }
        return commandLine;
    }

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

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    protected record CliExecution(boolean success, String output, Integer exitCode) { }
    private record ReadResult(boolean success, String text) {
        static ReadResult success(String text) { return new ReadResult(true, text); }
        static ReadResult failure() { return new ReadResult(false, ""); }
    }
}
