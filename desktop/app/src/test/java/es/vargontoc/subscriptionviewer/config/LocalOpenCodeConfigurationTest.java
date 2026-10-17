package es.vargontoc.subscriptionviewer.config;

import es.vargontoc.subscriptionviewer.context.ApplicationContextConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class LocalOpenCodeConfigurationTest {

    private static final String EXAMPLE_MARKER = "<YOUR_OPENCODE_API_KEY>";

    @Test
    void localConfigurationFileIsIgnoredByGit() throws IOException, InterruptedException {
        Path repositoryRoot = repositoryRoot();
        Process process = new ProcessBuilder("git", "check-ignore", "desktop/app/config/application.yml")
                .directory(repositoryRoot.toFile())
                .redirectErrorStream(true)
                .start();

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(process.waitFor()).as("git check-ignore output: %s", output).isZero();
        assertThat(output).contains("desktop/app/config/application.yml");
    }

    @Test
    void exampleContainsOnlyTheRecognizablePlaceholder() throws IOException {
        Path example = Path.of("config", "application.yml.example");
        String content = Files.readString(example, StandardCharsets.UTF_8);

        assertThat(content).isEqualTo("""
                subscription-viewer:
                  opencode:
                    api-key: "<YOUR_OPENCODE_API_KEY>"
                """);
        assertThat(content).contains(EXAMPLE_MARKER);
    }

    @Test
    void localConfigurationOverridesPackagedConfiguration() throws IOException {
        Path temporaryDirectory = Files.createTempDirectory("subscription-viewer-config");
        Path localConfigDirectory = Files.createDirectories(temporaryDirectory.resolve("config"));
        Files.writeString(localConfigDirectory.resolve("application.yml"), """
                subscription-viewer:
                  opencode:
                    api-key: LOCAL-SENTINEL-API-KEY
                """, StandardCharsets.UTF_8);

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(ApplicationContextConfig.class)
                .properties(
                        "spring.config.additional-location=" + localConfigDirectory.toUri(),
                        "spring.main.web-application-type=none",
                        "spring.main.banner-mode=off")
                .run()) {
            ConfigurationProvider provider = context.getBean(ConfigurationProvider.class);

            assertThat(provider.getOpenCodeApiKey()).isNotNull();
            assertThat(provider.getOpenCodeApiKey().value()).isEqualTo("LOCAL-SENTINEL-API-KEY");
        } finally {
            deleteRecursively(temporaryDirectory);
        }
    }

    private static Path repositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null && !Files.isDirectory(current.resolve(".git"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("No se ha encontrado la raíz del repositorio");
        }
        return current;
    }

    private static void deleteRecursively(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted((left, right) -> right.compareTo(left)).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException exception) {
                    throw new IllegalStateException("No se ha podido limpiar el directorio temporal", exception);
                }
            });
        }
    }
}
