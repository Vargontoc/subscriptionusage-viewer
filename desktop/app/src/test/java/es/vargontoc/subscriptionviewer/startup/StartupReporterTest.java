package es.vargontoc.subscriptionviewer.startup;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import es.vargontoc.subscriptionviewer.context.ApplicationContextConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pruebas de {@link StartupReporter}.
 *
 * <p>Verifican que el mensaje de aplicación lista solo se emite cuando se han
 * producido tanto el arranque del contexto Spring como la señal de bandeja
 * lista, evitando falsos positivos.</p>
 */
@SpringBootTest(classes = {ApplicationContextConfig.class, StartupReporterTest.TestConfig.class})
@ExtendWith(OutputCaptureExtension.class)
@ActiveProfiles("startup-reporter-test")
@DirtiesContext
class StartupReporterTest {

    @Autowired
    private StartupReporter startupReporter;

    /**
     * Configuración de test que publica un {@link TrayReadyEvent} cuando el
     * contexto de Spring emite {@link ApplicationReadyEvent}. Así se simula la
     * señal de bandeja en entornos headless donde {@code TrayIndicator} no puede
     * publicarla.
     */
    @TestConfiguration
    @Profile("startup-reporter-test")
    static class TestConfig {

        @Bean
        TrayReadySimulator trayReadySimulator(ApplicationEventPublisher publisher) {
            return new TrayReadySimulator(publisher);
        }
    }

    static class TrayReadySimulator implements ApplicationListener<ApplicationReadyEvent> {

        private final ApplicationEventPublisher publisher;

        TrayReadySimulator(ApplicationEventPublisher publisher) {
            this.publisher = publisher;
        }

        @Override
        public void onApplicationEvent(ApplicationReadyEvent event) {
            publisher.publishEvent(new TrayReadyEvent(this));
        }
    }

    @Test
    void logsReadyMessageAfterContextAndTraySignals(CapturedOutput output) {
        assertThat(output.getOut())
                .as("Debe aparecer el mensaje inequívoco de aplicación lista")
                .contains("Aplicación lista");
    }

    @Test
    void doesNotLogReadyMessageWhenOnlyContextIsReady() {
        ListAppender<ILoggingEvent> appender = createAndAttachAppender();
        try {
            StartupReporter reporter = new StartupReporter();
            ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);

            reporter.onApplicationReady(new ApplicationReadyEvent(mock(SpringApplication.class), new String[0], context, Duration.ZERO));

            assertThat(appender.list)
                    .as("El mensaje de listo no debe emitirse sin la señal de bandeja")
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .noneMatch(message -> message.contains("Aplicación lista"));
        } finally {
            detachAppender(appender);
        }
    }

    @Test
    void logsReadyMessageWhenTraySignalArrivesAfterContextReady() {
        ListAppender<ILoggingEvent> appender = createAndAttachAppender();
        try {
            StartupReporter reporter = new StartupReporter();
            ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);

            reporter.onApplicationReady(new ApplicationReadyEvent(mock(SpringApplication.class), new String[0], context, Duration.ZERO));
            reporter.onTrayReady(new TrayReadyEvent(new Object()));

            assertThat(appender.list)
                    .as("El mensaje de listo debe emitirse tras recibir ambas señales")
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .anyMatch(message -> message.contains("Aplicación lista"));
        } finally {
            detachAppender(appender);
        }
    }

    private ListAppender<ILoggingEvent> createAndAttachAppender() {
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(StartupReporter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void detachAppender(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(StartupReporter.class);
        logger.detachAppender(appender);
    }
}
