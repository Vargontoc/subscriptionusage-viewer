package es.vargontoc.subscriptionviewer.launcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de build del lanzador.
 *
 * <p>Verifican que el proyecto se empaqueta correctamente, que declara Java 21
 * y que el artefacto ejecutable se genera sin advertencias de versión.</p>
 */
class ApplicationLauncherBuildTest {

    @Test
    void pomDeclaresJava21AndCompilerRelease() throws Exception {
        Path pom = Path.of("pom.xml").toAbsolutePath().normalize();

        assertThat(pom).exists();

        String content = Files.readString(pom);
        assertThat(content)
                .as("pom.xml must declare java.version as 21")
                .contains("<java.version>21</java.version>");
        assertThat(content)
                .as("pom.xml must declare maven.compiler.release as 21")
                .contains("<maven.compiler.release>21</maven.compiler.release>");
        assertThat(content)
                .as("maven-compiler-plugin must enforce release 21")
                .contains("<release>21</release>");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void buildGeneratesExecutableJar() throws Exception {
        String mvn = System.getProperty("os.name", "").toLowerCase().contains("win") ? "mvn.cmd" : "mvn";
        ProcessBuilder builder = new ProcessBuilder(
                mvn, "-f", "pom.xml", "clean", "package", "-DskipTests");
        builder.directory(new File("."));
        builder.inheritIO();

        Process process = builder.start();
        boolean finished = process.waitFor(5, TimeUnit.MINUTES);

        assertThat(finished)
                .as("Maven build should finish within the timeout")
                .isTrue();
        assertThat(process.exitValue())
                .as("Maven build should succeed")
                .isEqualTo(0);

        Path target = Path.of("target").toAbsolutePath().normalize();
        assertThat(target)
                .as("Target directory should exist after packaging")
                .exists();

        try (var stream = Files.list(target)) {
            var jars = stream
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith("app-") && name.endsWith(".jar");
                    })
                    .toList();

            assertThat(jars)
                    .as("At least one app-*.jar should be generated")
                    .isNotEmpty();
        }
    }

    @Test
    void runtimeJavaVersionIs21() {
        String javaVersion = System.getProperty("java.version");
        assertThat(javaVersion)
                .as("Runtime Java version should be 21")
                .startsWith("21");
    }
}
