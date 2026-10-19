package es.vargontoc.subscriptionviewer.usage;

import com.sun.net.httpserver.HttpServer;
import es.vargontoc.subscriptionviewer.config.ConfigurationProvider;
import es.vargontoc.subscriptionviewer.usage.openai.OpenAiCredentials;
import es.vargontoc.subscriptionviewer.usage.openai.OpenAiCredentialsReadResult;
import es.vargontoc.subscriptionviewer.usage.openai.OpenAiCredentialsReader;
import es.vargontoc.subscriptionviewer.usage.openai.OpenAiUsageClient;
import es.vargontoc.subscriptionviewer.usage.openai.OpenAiUsageSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Comprueba el cableado del poller con la fuente real de OpenAI. */
class OpenAiPollingIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");

    @Test
    void unaRespuestaValidaLlegaAlHistorialYAlPublicador() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(200, "{\"plan_type\":\"plus\",\"rate_limit\":"
                + "{\"primary_window\":{\"used_percent\":6,\"reset_at\":1792344925},"
                + "\"secondary_window\":{\"used_percent\":1,\"reset_at\":1792344925}}}", requests);
        try {
            OpenAiUsageSource source = source(server, credentialsResult(
                    new OpenAiCredentials("token", "account")));
            UsageHistory history = new UsageHistory();
            UsageSnapshotPublisher publisher = new UsageSnapshotPublisher(Runnable::run);
            UsagePoller poller = poller(source, history, publisher);

            poller.runCycle();

            assertThat(requests).hasValue(1);
            assertThat(history.getSeries("openai", UsageWindowType.FIVE_HOUR))
                    .extracting(UsageHistoryPoint::usedPercentage).containsExactly(6);
            assertThat(history.getSeries("openai", UsageWindowType.SEVEN_DAY))
                    .extracting(UsageHistoryPoint::usedPercentage).containsExactly(1);
            assertThat(publisher.getCurrentSnapshot().readingOf("openai")).isPresent();
            assertThat(publisher.getCurrentSnapshot().stateOf("openai").orElseThrow().estado())
                    .isEqualTo(UsageProviderState.Estado.CORRECTO);
            poller.stop();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void un401SePublicaComoSesionCaducadaYSeReintentaEnElSiguienteCiclo() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(401, "", requests);
        try {
            OpenAiUsageSource source = source(server, credentialsResult(
                    new OpenAiCredentials("token", "account")));
            UsageSnapshotPublisher publisher = new UsageSnapshotPublisher(Runnable::run);
            UsagePoller poller = poller(source, new UsageHistory(), publisher);

            poller.runCycle();
            poller.runCycle();

            assertThat(requests).hasValue(2);
            assertThat(publisher.getCurrentSnapshot().stateOf("openai").orElseThrow().estado())
                    .isEqualTo(UsageProviderState.Estado.SESION_CADUCADA);
            poller.stop();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sinCredencialesSeReleeLaFuenteEnCadaCicloSinPeticion() {
        OpenAiCredentialsReader reader = mock(OpenAiCredentialsReader.class);
        OpenAiUsageClient client = mock(OpenAiUsageClient.class);
        OpenAiCredentialsReadResult noCredentials = mock(OpenAiCredentialsReadResult.class);
        when(noCredentials.credentials()).thenReturn(Optional.empty());
        when(reader.read()).thenReturn(noCredentials);
        OpenAiUsageSource source = new OpenAiUsageSource(reader, client, mock(ConfigurationProvider.class));
        UsagePoller poller = poller(source, new UsageHistory(), new UsageSnapshotPublisher(Runnable::run));

        poller.runCycle();
        poller.runCycle();

        verify(reader, org.mockito.Mockito.times(2)).read();
        verifyNoInteractions(client);
        poller.stop();
    }

    @Test
    void falloDeOpenAiNoAlteraLosOtrosProveedores() {
        UsageSource openAiFailure = new UsageSource() {
            @Override public String getProviderId() { return "openai"; }
            @Override public Optional<UsageReading> doRead() { return Optional.empty(); }
        };
        UsageSource claude = fixedSource("claude", 20);
        UsageSource opencode = fixedSource("opencode", 30);
        UsageSnapshotPublisher publisher = new UsageSnapshotPublisher(Runnable::run);
        UsagePoller poller = new UsagePoller(mock(ConfigurationProvider.class),
                List.of(openAiFailure, claude, opencode), new UsageHistory(), publisher,
                Clock.fixed(NOW, ZoneOffset.UTC), Executors.newSingleThreadScheduledExecutor());

        poller.runCycle();

        assertThat(publisher.getCurrentSnapshot().readingOf("openai")).isEmpty();
        assertThat(publisher.getCurrentSnapshot().readingOf("claude")).isPresent();
        assertThat(publisher.getCurrentSnapshot().readingOf("opencode")).isPresent();
        poller.stop();
    }

    private OpenAiUsageSource source(HttpServer server, OpenAiCredentialsReadResult credentials) {
        OpenAiCredentialsReader reader = mock(OpenAiCredentialsReader.class);
        when(reader.read()).thenReturn(credentials);
        OpenAiUsageClient client = new OpenAiUsageClient(HttpClient.newHttpClient(),
                URI.create("http://localhost:" + server.getAddress().getPort() + "/usage"));
        return new OpenAiUsageSource(reader, client, mock(ConfigurationProvider.class));
    }

    private OpenAiCredentialsReadResult credentialsResult(OpenAiCredentials credentials) {
        OpenAiCredentialsReadResult result = mock(OpenAiCredentialsReadResult.class);
        when(result.credentials()).thenReturn(Optional.of(credentials));
        return result;
    }

    private UsagePoller poller(UsageSource source, UsageHistory history,
                               UsageSnapshotPublisher publisher) {
        return new UsagePoller(mock(ConfigurationProvider.class), List.of(source), history,
                publisher, Clock.fixed(NOW, ZoneOffset.UTC),
                Executors.newSingleThreadScheduledExecutor());
    }

    private HttpServer server(int status, String body, AtomicInteger requests) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/usage", exchange -> {
            requests.incrementAndGet();
            byte[] response = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();
        return server;
    }

    private UsageSource fixedSource(String provider, int percentage) {
        return new UsageSource() {
            @Override public String getProviderId() { return provider; }
            @Override public Optional<UsageReading> doRead() {
                return Optional.of(new UsageReading(provider, NOW, List.of(
                        new UsageWindowReading(UsageWindowType.FIVE_HOUR, percentage, NOW.plusSeconds(3600)))));
            }
        };
    }
}
