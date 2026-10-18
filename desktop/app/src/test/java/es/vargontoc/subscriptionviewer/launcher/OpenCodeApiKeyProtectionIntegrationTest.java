package es.vargontoc.subscriptionviewer.launcher;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import es.vargontoc.subscriptionviewer.config.AppProperties;
import es.vargontoc.subscriptionviewer.config.ConfigurationProvider;
import es.vargontoc.subscriptionviewer.ui.UsageProviderCardPanel;
import es.vargontoc.subscriptionviewer.ui.UsageWindow;
import es.vargontoc.subscriptionviewer.usage.CountdownEvaluator;
import es.vargontoc.subscriptionviewer.usage.UsageHistory;
import es.vargontoc.subscriptionviewer.usage.UsageProviderState;
import es.vargontoc.subscriptionviewer.usage.UsageReadResult;
import es.vargontoc.subscriptionviewer.usage.UsageSnapshotPublisher;
import es.vargontoc.subscriptionviewer.usage.UsagePoller;
import es.vargontoc.subscriptionviewer.usage.opencode.OpenCodeUsageSource;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

import javax.swing.JLabel;
import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba la protección de la credencial recorriendo la ruta fuente -> estado
 * publicado -> tarjeta, además de la configuración y el registro de arranque.
 */
class OpenCodeApiKeyProtectionIntegrationTest {

    private static final String SENTINEL = "SENTINEL-API-KEY-12345-TEST";
    private static final String RESPONSE = "{\"usage\":{\"rolling\":{\"percent\":30,"
            + "\"resetsAt\":\"2099-01-01T00:00:00Z\"}}}";

    @Test
    @Disabled("v1 - retirar en TASK-021")
    void validKeyNeverReachesLogsOrUi() throws Exception {
        Scenario scenario = scenario(200, RESPONSE);
        try {
            UsageReadResult result = scenario.source.read();
            scenario.publisher.publish("opencode", result);
            assertNoSecret(scenario, cardTexts(scenario));
            assertThat(scenario.authorization.get()).isEqualTo("Bearer " + SENTINEL);
        } finally {
            scenario.close();
        }
    }

    @Test
    @Disabled("v1 - retirar en TASK-021")
    void absentKeyPublishesStateWithoutHttpRequest() throws Exception {
        Scenario scenario = scenario(null, null);
        try {
            UsageReadResult result = scenario.source.read();
            scenario.publisher.publish("opencode", result);
            assertThat(result.estado()).isEqualTo(UsageReadResult.Estado.CLAVE_AUSENTE);
            assertThat(scenario.requests.get()).isZero();
            assertNoSecret(scenario, cardTexts(scenario));
        } finally {
            scenario.close();
        }
    }

    @Test
    @Disabled("v1 - retirar en TASK-021")
    void unauthorizedResponseNeverLeaksKeyOrAuthorization() throws Exception {
        assertErrorScenario(401);
    }

    @Test
    @Disabled("v1 - retirar en TASK-021")
    void forbiddenResponseNeverLeaksKeyOrAuthorization() throws Exception {
        assertErrorScenario(403);
    }

    @Test
    void networkFailureNeverLeaksKeyOrAuthorization() throws Exception {
        Scenario scenario = scenario(0, null);
        try {
            UsageReadResult result = scenario.source.read();
            scenario.publisher.publish("opencode", result);
            assertThat(result.estado()).isEqualTo(UsageReadResult.Estado.FALLO_TRANSITORIO);
            assertNoSecret(scenario, cardTexts(scenario));
        } finally {
            scenario.close();
        }
    }

    @Test
    void localConfigurationIsIgnoredByGit() throws Exception {
        Path root = repositoryRoot();
        Process process = new ProcessBuilder("git", "check-ignore", "desktop/app/config/application.yml")
                .directory(root.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor()).as("git check-ignore output: %s", output).isZero();
        assertThat(output).contains("desktop/app/config/application.yml");
    }

    private void assertErrorScenario(int status) throws Exception {
        Scenario scenario = scenario(status, "");
        try {
            UsageReadResult result = scenario.source.read();
            scenario.publisher.publish("opencode", result);
            assertThat(result.estado()).isIn(UsageReadResult.Estado.CLAVE_INVALIDA,
                    UsageReadResult.Estado.SIN_SUSCRIPCION);
            assertNoSecret(scenario, cardTexts(scenario));
        } finally {
            scenario.close();
        }
    }

    private Scenario scenario(Integer status, String response) throws IOException {
        HttpServer server = null;
        AtomicInteger requests = new AtomicInteger();
        AtomicReferenceString authorization = new AtomicReferenceString();
        if (status != null) {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            HttpServer activeServer = server;
            server.createContext("/usage", exchange -> {
                requests.incrementAndGet();
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                byte[] body = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
        }

        MockEnvironment environment = new MockEnvironment();
        AppProperties properties = new AppProperties();
        if (status != null) {
            environment.setProperty("subscription-viewer.opencode.api-key", SENTINEL);
            environment.setProperty("subscription-viewer.opencode.usage-url",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/usage");
        } else {
            // La URL no se consulta cuando no existe clave, pero se mantiene una
            // configuración válida para ensamblar el mismo camino de aplicación.
            environment.setProperty("subscription-viewer.opencode.usage-url", "http://127.0.0.1:1/usage");
        }
        ConfigurationProvider configuration = new ConfigurationProvider(properties, environment);
        Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext((LoggerContext) logger.getLoggerContext());
        appender.start();
        logger.addAppender(appender);
        configuration.logResolvedProperties();

        OpenCodeUsageSource source = new OpenCodeUsageSource(new ObjectMapper(), configuration);
        UsageSnapshotPublisher publisher = new UsageSnapshotPublisher();
        UsageHistory history = new UsageHistory();
        UsagePoller poller = new UsagePoller(configuration, List.of(source), history, publisher, Clock.systemUTC());
        UsageWindow window = new UsageWindow(publisher, new CountdownEvaluator(Clock.systemUTC()), history);
        window.subscribe();
        return new Scenario(server, source, poller, publisher, requests, authorization, appender, logger);
    }

    private List<String> cardTexts(Scenario scenario) {
        UsageProviderState state = scenario.publisher.getCurrentSnapshot().providerStates().get("opencode");
        UsageProviderCardPanel card = new UsageProviderCardPanel("opencode",
                new CountdownEvaluator(Clock.systemUTC()));
        card.update(state);
        List<String> texts = new ArrayList<>();
        collectTexts(card, texts);
        return texts;
    }

    private void collectTexts(Component component, List<String> texts) {
        if (component instanceof JLabel label && label.getText() != null) {
            texts.add(label.getText());
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collectTexts(child, texts);
            }
        }
    }

    private void assertNoSecret(Scenario scenario, List<String> uiTexts) {
        List<String> logs = scenario.appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        List<String> all = new ArrayList<>(logs);
        all.addAll(uiTexts);
        String joined = String.join("\n", all);
        assertThat(joined).doesNotContain(SENTINEL, "Authorization", SENTINEL.substring(SENTINEL.length() - 4));
        assertThat(logs).noneMatch(text -> text.contains("Bearer") || text.contains(SENTINEL));
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

    private record Scenario(HttpServer server, OpenCodeUsageSource source, UsagePoller poller,
                            UsageSnapshotPublisher publisher,
                            AtomicInteger requests, AtomicReferenceString authorization,
                            ListAppender<ILoggingEvent> appender, Logger logger) {
        void close() {
            logger.detachAppender(appender);
            appender.stop();
            if (server != null) {
                server.stop(0);
            }
        }
    }

    private static final class AtomicReferenceString {
        private volatile String value;

        void set(String value) { this.value = value; }

        String get() { return value; }
    }
}
