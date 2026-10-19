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
import java.math.BigDecimal;
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
             assertThat(provider.getUsageStateFile().toString()).endsWith(".claude" + java.io.File.separator + "statusline-dump.json");
             assertThat(provider.getUsagePollInterval()).isEqualTo(Duration.ofSeconds(10));
             assertThat(provider.getOpenCodeUsagePollInterval()).isEqualTo(Duration.ofSeconds(60));
             assertThat(provider.getOpenAiUsagePollInterval()).isEqualTo(Duration.ofSeconds(60));
        });
    }

    @Test
    void shouldReportOpenCodeSectionWhenItIsPresent() {
        contextRunner.withPropertyValues("subscription-viewer.opencode.cycle-day=20")
                .run(context -> assertThat(context.getBean(ConfigurationProvider.class)
                        .isOpenCodeConfigured()).isTrue());
    }

    @Test
    void shouldReportOpenCodeSectionAbsentDespiteDefaultPropertiesObject() {
        contextRunner.run(context -> assertThat(context.getBean(ConfigurationProvider.class)
                .isOpenCodeConfigured()).isFalse());
    }

    @Test
    void shouldReportOpenCodeSectionPresentWithoutOwnKeys() {
        contextRunner.withPropertyValues("subscription-viewer.opencode=")
                .run(context -> assertThat(context.getBean(ConfigurationProvider.class)
                        .isOpenCodeConfigured()).isTrue());
    }

    @Test
    void shouldNormalizeOpenCodePollIntervalToTenSeconds() {
        contextRunner
                .withPropertyValues("subscription-viewer.opencode.usage-poll-interval=5s")
                .run(context -> assertThat(context.getBean(ConfigurationProvider.class)
                        .getOpenCodeUsagePollInterval()).isEqualTo(Duration.ofSeconds(10)));
    }

    @Test
    void shouldUseOpenAiDefaultsAndReadConfiguredInterval() {
        contextRunner.run(context -> assertThat(context.getBean(ConfigurationProvider.class)
                .getOpenAiUsagePollInterval()).isEqualTo(Duration.ofSeconds(60)));
        contextRunner.withPropertyValues("subscription-viewer.openai.usage-poll-interval= ")
                .run(context -> assertThat(context.getBean(ConfigurationProvider.class)
                        .getOpenAiUsagePollInterval()).isEqualTo(Duration.ofSeconds(60)));
        contextRunner.withPropertyValues("subscription-viewer.openai.usage-poll-interval=30s")
                .run(context -> assertThat(context.getBean(ConfigurationProvider.class)
                        .getOpenAiUsagePollInterval()).isEqualTo(Duration.ofSeconds(30)));
    }

    @Test
    void shouldApplyMinimumToOpenAiAndOpenCodeIntervals() {
        contextRunner.withPropertyValues("subscription-viewer.openai.usage-poll-interval=2s")
                .run(context -> assertThat(context.getBean(ConfigurationProvider.class)
                        .getOpenAiUsagePollInterval()).isEqualTo(Duration.ofSeconds(10)));
        contextRunner.withPropertyValues("subscription-viewer.opencode.usage-poll-interval=2s")
                .run(context -> assertThat(context.getBean(ConfigurationProvider.class)
                        .getOpenCodeUsagePollInterval()).isEqualTo(Duration.ofSeconds(10)));
    }

    @Test
    void shouldRejectInvalidIntervalsAndIdentifyTheProperty() {
        for (String value : List.of("abc", "0s", "-5s")) {
            contextRunner.withPropertyValues("subscription-viewer.openai.usage-poll-interval=" + value)
                    .run(context -> assertThat(context.getStartupFailure()).hasStackTraceContaining(
                            "subscription-viewer.openai.usage-poll-interval"));
        }
        contextRunner.withPropertyValues("subscription-viewer.claude.usage-poll-interval=abc")
                .run(context -> assertThat(context.getStartupFailure()).hasStackTraceContaining(
                        "subscription-viewer.claude.usage-poll-interval"));
        contextRunner.withPropertyValues("subscription-viewer.opencode.usage-poll-interval=abc")
                .run(context -> assertThat(context.getStartupFailure()).hasStackTraceContaining(
                        "subscription-viewer.opencode.usage-poll-interval"));
    }

    @Test
    void shouldReadCycleDayCapsAndTimeout() {
        contextRunner.withPropertyValues(
                        "subscription-viewer.opencode.cycle-day=20",
                        "subscription-viewer.opencode.default-cap-usd=75",
                        "subscription-viewer.opencode.poll-interval-seconds=25",
                        "subscription-viewer.opencode.command-timeout-seconds=45",
                        "subscription-viewer.opencode.model-caps[0].model=opencode-go/gpt-5",
                        "subscription-viewer.opencode.model-caps[0].cap-usd=120")
                .run(context -> {
                    ConfigurationProvider provider = context.getBean(ConfigurationProvider.class);
                    assertThat(provider.getOpenCodeCycleDay()).isEqualTo(20);
                    assertThat(provider.getOpenCodeDefaultCapUsd()).isEqualByComparingTo("75");
                    assertThat(provider.getOpenCodePollInterval()).isEqualTo(Duration.ofSeconds(25));
                    assertThat(provider.getOpenCodeCommandTimeout()).isEqualTo(Duration.ofSeconds(45));
                    assertThat(provider.getOpenCodeModelCap("opencode-go/gpt-5"))
                            .isEqualByComparingTo(BigDecimal.valueOf(120));
                });
    }

    @Test
    void shouldNormalizeInvalidCycleDayAndEmptyCap() {
        for (String day : List.of("0", "32", "not-a-number")) {
            contextRunner.withPropertyValues(
                            "subscription-viewer.opencode.cycle-day=" + day,
                            "subscription-viewer.opencode.model-caps[0].model=example-model",
                            "subscription-viewer.opencode.model-caps[0].cap-usd= ")
                    .run(context -> {
                        ConfigurationProvider provider = context.getBean(ConfigurationProvider.class);
                        assertThat(provider.getOpenCodeCycleDay()).isEqualTo(1);
                        assertThat(provider.getOpenCodeModelCap("example-model"))
                                .isEqualByComparingTo("60");
                    });
        }
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
    void shouldReadClaudeOverride() {
        contextRunner.withPropertyValues(
                        "subscription-viewer.claude.usage-state-file=~/alternative-status.json",
                        "subscription-viewer.claude.usage-poll-interval=15s")
                .run(context -> {
                    ConfigurationProvider provider = context.getBean(ConfigurationProvider.class);
                    assertThat(provider.getUsageStateFile().toString()).endsWith("alternative-status.json");
                    assertThat(provider.getUsagePollInterval()).isEqualTo(Duration.ofSeconds(15));
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
