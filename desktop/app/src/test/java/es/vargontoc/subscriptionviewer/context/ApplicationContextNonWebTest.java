package es.vargontoc.subscriptionviewer.context;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de contexto Spring Boot en modo non-web.
 *
 * <p>Verifican que el contenedor se inicializa sin servidor web embebido y
 * que forzar el modo servlet no abre ningún socket de red.</p>
 */
@SpringBootTest(classes = ApplicationContextConfig.class)
class ApplicationContextNonWebTest {

    @Autowired
    private ConfigurableApplicationContext context;

    @Test
    void contextIsActive() {
        assertThat(context.isActive()).isTrue();
        assertThat(context.isRunning()).isTrue();
    }

    @Test
    void noWebServerFactoryBeanIsPresent() {
        assertThat(context.getBeanProvider(org.springframework.boot.web.server.WebServerFactory.class).getIfAvailable())
                .as("No web server factory should be registered in non-web mode")
                .isNull();
    }

    @Test
    void servletModeWithoutWebStarterDoesNotExposePort() {
        try (ConfigurableApplicationContext servletContext = new SpringApplicationBuilder(ApplicationContextConfig.class)
                .properties("spring.main.web-application-type=servlet")
                .run()) {
            assertThat(servletContext.isActive()).isTrue();
            assertThat(servletContext.getBeanProvider(org.springframework.boot.web.server.WebServerFactory.class).getIfAvailable())
                    .as("No web server factory should be registered even when servlet mode is forced without web starter")
                    .isNull();
        }
    }
}
