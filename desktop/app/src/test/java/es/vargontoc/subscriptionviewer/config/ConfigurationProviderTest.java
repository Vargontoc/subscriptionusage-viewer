package es.vargontoc.subscriptionviewer.config;

import es.vargontoc.subscriptionviewer.context.ApplicationContextConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas unitarias del proveedor de configuración.
 *
 * <p>Verifica que las propiedades externalizadas se resuelven correctamente
 * cuando se proporciona un fichero externo y que, en su ausencia, se aplican
 * los valores por defecto documentados.</p>
 */
class ConfigurationProviderTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ApplicationContextConfig.class);

    @Test
    void shouldApplyDefaultPropertiesWhenNoExternalFileIsProvided() {
        contextRunner.run(context -> {
            ConfigurationProvider provider = context.getBean(ConfigurationProvider.class);

            assertThat(provider.getAppName()).isEqualTo("Subscription Usage Viewer");
            assertThat(provider.getTrayIconPath()).isEqualTo("icons/app-icon.png");
            assertThat(provider.getUsageStateFile().toString()).endsWith(".claude" + java.io.File.separator + "statusline-state.json");
             assertThat(provider.getUsagePollInterval()).isEqualTo(Duration.ofSeconds(10));
             assertThat(provider.getOpenCodeUsagePollInterval()).isEqualTo(Duration.ofSeconds(60));
             assertThat(provider.getOpenCodeBackoffMaxInterval()).isEqualTo(Duration.ofSeconds(300));
             assertThat(provider.getOpenCodeUsageUrl()).isEqualTo("https://opencode.ai/zen/go/v1/usage");
             assertThat(provider.getOpenCodeApiKey()).isNull();
        });
    }

    @Test
    void shouldNormalizeOpenCodePollIntervalToTenSeconds() {
        contextRunner
                .withPropertyValues("subscription-viewer.opencode.usage-poll-interval=5s")
                .run(context -> assertThat(context.getBean(ConfigurationProvider.class)
                        .getOpenCodeUsagePollInterval()).isEqualTo(Duration.ofSeconds(10)));
    }

    @Test
    void shouldReadExternalPropertiesWhenProvided() {
        contextRunner
                .withInitializer(loadYaml("config/application-external.yml"))
                .run(context -> {
                    ConfigurationProvider provider = context.getBean(ConfigurationProvider.class);

                    assertThat(provider.getAppName()).isEqualTo("External");
                    assertThat(provider.getUsageStateFile().toString()).endsWith("custom-status.json");
                    assertThat(provider.getUsagePollInterval()).isEqualTo(Duration.ofSeconds(30));
                });
    }

    @Test
    void shouldExposeConfiguredOpenCodeApiKey() {
        contextRunner
                .withPropertyValues("subscription-viewer.opencode.api-key=SENTINEL-KEY-12345")
                .run(context -> assertThat(context.getBean(ConfigurationProvider.class)
                        .getOpenCodeApiKey().value()).isEqualTo("SENTINEL-KEY-12345"));
    }

    @Test
    void shouldUseEnvironmentWhenYamlDoesNotDefineTheKey() {
        contextRunner
                .withPropertyValues("OPENCODE_API_KEY=ENV-KEY-123")
                .run(context -> {
                    ConfigurationProvider provider = context.getBean(ConfigurationProvider.class);

                    assertThat(provider.getOpenCodeApiKey().value()).isEqualTo("ENV-KEY-123");
                    assertThat(provider.getOpenCodeApiKeyOrigin()).isEqualTo("entorno");
                    assertThat(provider.hasOpenCodeApiKey()).isTrue();
                });
    }

    @Test
    void shouldPreferYamlOverEnvironment() {
        contextRunner
                .withPropertyValues(
                        "subscription-viewer.opencode.api-key=YAML-KEY-123",
                        "OPENCODE_API_KEY=ENV-KEY-456")
                .run(context -> {
                    ConfigurationProvider provider = context.getBean(ConfigurationProvider.class);

                    assertThat(provider.getOpenCodeApiKey().value()).isEqualTo("YAML-KEY-123");
                    assertThat(provider.getOpenCodeApiKeyOrigin()).isEqualTo("application.yml");
                });
    }

    @Test
    void shouldTreatEmptyAndExampleMarkerAsMissing() {
        contextRunner
                .withPropertyValues("subscription-viewer.opencode.api-key= ")
                .run(context -> assertThat(context.getBean(ConfigurationProvider.class).hasOpenCodeApiKey())
                        .isFalse());

        contextRunner
                .withPropertyValues("subscription-viewer.opencode.api-key=<YOUR_OPENCODE_API_KEY>")
                .run(context -> {
                    ConfigurationProvider provider = context.getBean(ConfigurationProvider.class);
                    assertThat(provider.getOpenCodeApiKey()).isNull();
                    assertThat(provider.getOpenCodeApiKeyOrigin()).isEqualTo("ninguno");
                });
    }

    @Test
    void shouldExpandTildeInUsageStateFile() {
        contextRunner
                .withInitializer(loadYaml("config/application-external.yml"))
                .run(context -> {
                    ConfigurationProvider provider = context.getBean(ConfigurationProvider.class);

                    Path stateFile = provider.getUsageStateFile();
                    assertThat(stateFile).isAbsolute();
                    assertThat(stateFile.toString()).doesNotContain("~");
                    assertThat(stateFile.toString()).startsWith(System.getProperty("user.home"));
                });
    }

    private static ApplicationContextInitializer<ConfigurableApplicationContext> loadYaml(String path) {
        return context -> {
            try {
                YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
                List<org.springframework.core.env.PropertySource<?>> sources = loader.load(
                        "application-external", new ClassPathResource(path));
                sources.forEach(source -> context.getEnvironment().getPropertySources().addFirst(source));
            } catch (IOException e) {
                throw new IllegalStateException("No se ha podido cargar el fichero de configuración de prueba: " + path, e);
            }
        };
    }
}
