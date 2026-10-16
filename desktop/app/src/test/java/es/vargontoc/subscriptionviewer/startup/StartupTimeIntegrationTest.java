package es.vargontoc.subscriptionviewer.startup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de integración que mide de extremo a extremo el tiempo de arranque de la
 * aplicación.
 *
 * <p>Extrae el artefacto ejecutable generado por el build, construye un
 * classpath completo con las dependencias empaquetadas y lanza el punto de
 * entrada en un proceso hijo mediante {@link ProcessBuilder}. Captura
 * {@code stdout}/{@code stderr} y cronometra el tiempo transcurrido hasta la
 * aparición del mensaje de aplicación lista. El caso feliz verifica que el
 * arranque completo se produce en menos de 30 segundos; el caso límite activa
 * un {@link BeanFactoryPostProcessor} lento para comprobar que el mecanismo
 * detecta cuando se supera dicho umbral.</p>
 */
class StartupTimeIntegrationTest {

    private static final String READY_MESSAGE = "Aplicación lista";
    private static final long MAX_STARTUP_SECONDS = 30;
    private static final long MAX_STARTUP_MILLIS = MAX_STARTUP_SECONDS * 1_000L;
    private static final String EXTRACTED_DIR = "app-extracted";

    @Test
    @Timeout(value = 45, unit = TimeUnit.SECONDS)
    void startsWithinThirtySeconds() throws Exception {
        ProcessResult result = runApplication("integration-test", MAX_STARTUP_MILLIS);

        assertThat(result.ready)
                .as("El mensaje de listo debe aparecer en la salida del proceso")
                .isTrue();
        assertThat(result.elapsedMillis)
                .as("El tiempo de arranque debe ser inferior a %d segundos", MAX_STARTUP_SECONDS)
                .isLessThan(MAX_STARTUP_MILLIS);
    }

    @Test
    @Timeout(value = 45, unit = TimeUnit.SECONDS)
    void failsWhenStartupExceedsThirtySeconds() throws Exception {
        ProcessResult result = runApplication("slow-startup", MAX_STARTUP_MILLIS);

        assertThat(result.ready)
                .as("Con el post-processor lento, el mensaje de listo no debe aparecer antes de %d segundos",
                        MAX_STARTUP_SECONDS)
                .isFalse();
        assertThat(result.elapsedMillis)
                .as("El test debe detectar que el arranque supera los %d segundos", MAX_STARTUP_SECONDS)
                .isGreaterThanOrEqualTo(MAX_STARTUP_MILLIS);
    }

    private ProcessResult runApplication(String profile, long waitMillis) throws Exception {
        Path jar = findExecutableJar();
        Path extracted = extractJar(jar);
        String classpath = buildClasspath(extracted);

        ProcessBuilder builder = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", classpath,
                "es.vargontoc.subscriptionviewer.launcher.ApplicationLauncher",
                "--spring.profiles.active=" + profile
        );
        builder.redirectErrorStream(true);

        long start = System.currentTimeMillis();
        Process process = builder.start();

        AtomicBoolean ready = new AtomicBoolean(false);
        Thread reader = startOutputReader(process, ready);

        reader.join(waitMillis);
        long elapsed = System.currentTimeMillis() - start;

        destroyProcess(process);
        reader.join(5_000L);

        return new ProcessResult(ready.get(), elapsed);
    }

    private Thread startOutputReader(Process process, AtomicBoolean readyFlag) {
        Thread reader = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.contains(READY_MESSAGE)) {
                        readyFlag.set(true);
                        break;
                    }
                }
            } catch (IOException e) {
                // El stream se cerró porque el proceso terminó; no se repropaga.
            }
        });
        reader.start();
        return reader;
    }

    private void destroyProcess(Process process) throws InterruptedException {
        process.destroy();
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly();
        }
    }

    private Path findExecutableJar() throws IOException {
        Path target = Path.of("target").toAbsolutePath().normalize();
        try (var stream = Files.list(target)) {
            return stream
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith("app-") && name.endsWith(".jar")
                                && !name.endsWith("-sources.jar")
                                && !name.endsWith("-javadoc.jar");
                    })
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No se encontró el JAR ejecutable en " + target));
        }
    }

    private Path extractJar(Path jar) throws IOException {
        Path extracted = Path.of("target", EXTRACTED_DIR).toAbsolutePath().normalize();
        if (Files.exists(extracted)) {
            deleteDirectory(extracted);
        }
        Files.createDirectories(extracted);

        try (JarFile jarFile = new JarFile(jar.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                Path entryPath = extracted.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    try (InputStream is = jarFile.getInputStream(entry)) {
                        Files.copy(is, entryPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
        return extracted;
    }

    private void deleteDirectory(Path directory) throws IOException {
        try (var walk = Files.walk(directory)) {
            for (Path path : walk.sorted((a, b) -> b.compareTo(a)).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private String buildClasspath(Path extracted) throws IOException {
        String separator = System.getProperty("path.separator");
        StringBuilder classpath = new StringBuilder();

        classpath.append(extracted.resolve("BOOT-INF/classes").toAbsolutePath().normalize());

        Path libDir = extracted.resolve("BOOT-INF/lib");
        if (Files.isDirectory(libDir)) {
            try (var stream = Files.list(libDir)) {
                for (Path jar : stream.filter(p -> p.toString().endsWith(".jar")).toList()) {
                    classpath.append(separator).append(jar.toAbsolutePath().normalize());
                }
            }
        }

        classpath.append(separator)
                .append(Path.of("target", "test-classes").toAbsolutePath().normalize());

        return classpath.toString();
    }

    private record ProcessResult(boolean ready, long elapsedMillis) {
    }
}
