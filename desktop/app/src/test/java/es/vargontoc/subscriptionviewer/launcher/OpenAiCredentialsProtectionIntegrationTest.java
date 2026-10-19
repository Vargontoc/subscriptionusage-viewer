package es.vargontoc.subscriptionviewer.launcher;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import es.vargontoc.subscriptionviewer.ui.UsageProviderCardPanel;
import es.vargontoc.subscriptionviewer.usage.UsageProviderState;
import es.vargontoc.subscriptionviewer.usage.UsageReadResult;
import es.vargontoc.subscriptionviewer.usage.UsageReading;
import es.vargontoc.subscriptionviewer.usage.CountdownEvaluator;
import es.vargontoc.subscriptionviewer.usage.UsageWindowReading;
import es.vargontoc.subscriptionviewer.usage.UsageWindowType;
import es.vargontoc.subscriptionviewer.usage.openai.OpenAiCredentials;
import es.vargontoc.subscriptionviewer.usage.openai.OpenAiCredentialsReader;
import es.vargontoc.subscriptionviewer.usage.openai.OpenAiUsageClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.swing.JLabel;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Verificación de que las credenciales y los datos personales no cruzan la frontera de la aplicación. */
class OpenAiCredentialsProtectionIntegrationTest {
    private static final String TOKEN = "OPENAI_SENTINEL_ACCESS_TOKEN_7f3a";
    private static final String ACCOUNT = "OPENAI_SENTINEL_ACCOUNT_91b2";
    private static final String USER_ID = "OPENAI_SENTINEL_USER_42c9";
    private static final String EMAIL = "openai-sentinel@example.invalid";
    private static final Instant WRITTEN_AT = Instant.parse("2026-10-19T12:00:00Z");

    @Test
    void validCredentialsDoNotLeakIntoLogsOrInterfaceAndFileIsReadOnly() throws Exception {
        Path auth = Files.createTempFile("codex-auth", ".json");
        String contents = authJson();
        Files.writeString(auth, contents, StandardCharsets.UTF_8);
        FileTime modified = Files.getLastModifiedTime(auth);

        try (TestServer server = new TestServer(200,
                "{\"rate_limit\":{\"primary_window\":{\"used_percent\":6,\"reset_after_seconds\":3600}}}")) {
            LogCapture logs = new LogCapture();
            try {
                var credentials = new OpenAiCredentialsReader(auth).read().credentials().orElseThrow();
                UsageReadResult result = new OpenAiUsageClient(HttpClient.newHttpClient(), server.uri()).read(credentials);
                assertThat(result.estado()).isEqualTo(UsageReadResult.Estado.DATOS);
                assertThat(server.authorization).isEqualTo("Bearer " + TOKEN);
                assertThat(server.account).isEqualTo(ACCOUNT);
                assertThat(logs.text()).doesNotContain(TOKEN, ACCOUNT, USER_ID, EMAIL, "Authorization");
            } finally {
                logs.close();
            }
        }

        assertThat(Files.readString(auth)).isEqualTo(contents);
        assertThat(Files.getLastModifiedTime(auth)).isEqualTo(modified);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    void expiredCredentialsDoNotLeakIntoLogsOrExpiredSessionInterface(int status) throws Exception {
        try (TestServer server = new TestServer(status, "{}")) {
            LogCapture logs = new LogCapture();
            try {
                UsageReadResult result = new OpenAiUsageClient(HttpClient.newHttpClient(), server.uri())
                        .read(new OpenAiCredentials(TOKEN, ACCOUNT));
                assertThat(result.estado()).isEqualTo(UsageReadResult.Estado.SESION_CADUCADA);
                assertThat(logs.text()).doesNotContain(TOKEN, ACCOUNT, USER_ID, EMAIL, "Authorization");
            } finally {
                logs.close();
            }
        }

        assumeFalse(GraphicsEnvironment.isHeadless(), "Entorno headless: se omite la comprobación Swing");
        UsageProviderCardPanel panel = new UsageProviderCardPanel("openai", new CountdownEvaluator(Clock.systemUTC()));
        panel.update(new UsageProviderState(UsageProviderState.Estado.SESION_CADUCADA, null, null));
        assertThat(labelTexts(panel)).allSatisfy(text -> assertThat(text)
                .doesNotContain(TOKEN, ACCOUNT, USER_ID, EMAIL, "Authorization"));
    }

    @Test
    void absentCredentialsDoNotMakeARequestOrExposeSentinels() throws Exception {
        Path absent = Files.createTempDirectory("missing-codex").resolve("auth.json");
        var read = new OpenAiCredentialsReader(absent).read();
        assertThat(read.available()).isFalse();

        LogCapture logs = new LogCapture();
        try {
            var result = new OpenAiUsageClient(HttpClient.newHttpClient(), URI.create("http://127.0.0.1:1/"))
                    .read(null);
            assertThat(result.estado()).isEqualTo(UsageReadResult.Estado.FALLO_TRANSITORIO);
            assertThat(logs.text()).doesNotContain(TOKEN, ACCOUNT, USER_ID, EMAIL, "Authorization");
        } finally {
            logs.close();
        }
    }

    @Test
    void networkFailureDoesNotExposeCredentialsOrModifyCredentialFile() throws Exception {
        Path auth = Files.createTempFile("codex-network-auth", ".json");
        String contents = authJson();
        Files.writeString(auth, contents, StandardCharsets.UTF_8);
        FileTime modified = Files.getLastModifiedTime(auth);

        LogCapture logs = new LogCapture();
        try {
            var credentials = new OpenAiCredentialsReader(auth).read().credentials().orElseThrow();
            var result = new OpenAiUsageClient(HttpClient.newHttpClient(), URI.create("http://127.0.0.1:1/"))
                    .read(credentials);
            assertThat(result.estado()).isEqualTo(UsageReadResult.Estado.FALLO_TRANSITORIO);
            assertThat(logs.text()).doesNotContain(TOKEN, ACCOUNT, USER_ID, EMAIL, "Authorization");
        } finally {
            logs.close();
        }

        assertThat(Files.readString(auth)).isEqualTo(contents);
        assertThat(Files.getLastModifiedTime(auth)).isEqualTo(modified);
    }

    private String authJson() {
        return "{\"tokens\":{\"access_token\":\"" + TOKEN + "\",\"account_id\":\""
                + ACCOUNT + "\",\"refresh_token\":\"" + USER_ID + "\",\"id_token\":\""
                + EMAIL + "\"},\"user_id\":\"" + USER_ID + "\",\"email\":\"" + EMAIL + "\"}";
    }

    private List<String> labelTexts(UsageProviderCardPanel panel) {
        return java.util.Arrays.stream(panel.getComponents())
                .filter(component -> component instanceof JLabel)
                .map(component -> ((JLabel) component).getText())
                .toList();
    }

    private static final class LogCapture implements AutoCloseable {
        private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        private final Logger root = (Logger) org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

        private LogCapture() {
            appender.start();
            root.addAppender(appender);
        }

        private String text() {
            return appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);
        }

        @Override
        public void close() {
            root.detachAppender(appender);
            appender.stop();
        }
    }

    private static final class TestServer implements AutoCloseable {
        private final com.sun.net.httpserver.HttpServer server;
        private volatile String authorization;
        private volatile String account;

        private TestServer(int status, String body) throws IOException {
            server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                authorization = exchange.getRequestHeaders().getFirst("Authorization");
                account = exchange.getRequestHeaders().getFirst("ChatGPT-Account-Id");
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, bytes.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(bytes);
                }
            });
            server.start();
        }

        private URI uri() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
