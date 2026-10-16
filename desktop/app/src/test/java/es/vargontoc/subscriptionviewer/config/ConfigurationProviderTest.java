package es.vargontoc.subscriptionviewer.config;

import es.vargontoc.subscriptionviewer.context.ApplicationContextConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
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
        });
    }

    @Test
    void shouldReadExternalPropertiesWhenProvided() {
        contextRunner
                .withInitializer(loadYaml("config/application-external.yml"))
                .run(context -> {
                    ConfigurationProvider provider = context.getBean(ConfigurationProvider.class);

                    assertThat(provider.getAppName()).isEqualTo("External");
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
