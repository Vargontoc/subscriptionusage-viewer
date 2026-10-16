package es.vargontoc.subscriptionviewer.context;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.List;

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
        assertThat(webServerFactoryBeanNames(context))
                .as("No web server factory should be registered in non-web mode")
                .isEmpty();
    }

    @Test
    void servletModeWithoutWebStarterDoesNotExposePort() {
        try (ConfigurableApplicationContext servletContext = new SpringApplicationBuilder(ApplicationContextConfig.class)
                .properties("spring.main.web-application-type=servlet")
                .run()) {
            assertThat(servletContext.isActive()).isTrue();
            assertThat(webServerFactoryBeanNames(servletContext))
                    .as("No web server factory should be registered even when servlet mode is forced without web starter")
                    .isEmpty();
        }
    }

    /**
     * Nombres de los beans cuyo tipo es una fábrica de servidor web. Se compara por
     * nombre de clase porque el tipo {@code WebServerFactory} no está en el classpath
     * sin el starter web (en Spring Boot 4 vive en un módulo separado).
     */
    private static List<String> webServerFactoryBeanNames(ConfigurableApplicationContext ctx) {
        return Arrays.stream(ctx.getBeanDefinitionNames())
                .filter(name -> {
                    Class<?> type = ctx.getType(name);
                    return type != null && type.getSimpleName().endsWith("WebServerFactory");
                })
                .toList();
    }
}
