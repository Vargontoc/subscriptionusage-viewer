package es.vargontoc.subscriptionviewer.usage.openai;

import es.vargontoc.subscriptionviewer.usage.UsageWindowType;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiUsageClientTest {
    private static final Instant QUERY_TIME = Instant.parse("2026-10-19T12:00:00Z");

    @Test
    void parsesPrimaryAndSecondaryByRoleAndIdentifiesPlus() {
        String json = """
                {"plan_type":"plus","rate_limit":{
                  "primary_window":{"used_percent":6,"reset_at":1792344925,"limit_window_seconds":123},
                  "secondary_window":{"used_percent":1,"reset_at":1792348525,"limit_window_seconds":456}}}
                """;

        var result = new OpenAiUsageClient().parse(json, QUERY_TIME).orElseThrow();

        assertThat(result.isPlus()).isTrue();
        assertThat(result.reading().planType()).isEqualTo("plus");
        assertThat(result.reading().windows()).extracting(w -> w.type())
                .containsExactly(UsageWindowType.FIVE_HOUR, UsageWindowType.SEVEN_DAY);
        assertThat(result.reading().windows()).extracting(w -> w.usedPercentage())
                .containsExactly(6.0, 1.0);
        assertThat(result.reading().windows().get(0).resetsAt())
                .isEqualTo(Instant.ofEpochSecond(1792344925));
    }

    @Test
    void ignoresDurationAndOptionalFields() {
        String json = "{\"rate_limit\":{\"primary_window\":{\"used_percent\":5.6,\"reset_at\":1792344925,"
                + "\"limit_window_seconds\":999,\"unknown\":\"ignored\"}}}";

        var result = new OpenAiUsageClient().parse(json, QUERY_TIME).orElseThrow();

        assertThat(result.planType()).isNull();
        assertThat(result.reading().windows()).hasSize(1);
        assertThat(result.reading().windows().get(0).usedPercentage()).isEqualTo(5.6);
    }

    @Test
    void preservesDecimalPercentagesWithoutRounding() {
        String json = "{\"rate_limit\":{\"primary_window\":{\"used_percent\":40," 
                + "\"reset_at\":1792344925},\"secondary_window\":{\"used_percent\":50.1," 
                + "\"reset_at\":1792348525}}}";

        var result = new OpenAiUsageClient().parse(json, QUERY_TIME).orElseThrow();

        assertThat(result.reading().windows()).extracting(w -> w.usedPercentage())
                .containsExactly(40.0, 50.1);
    }

    @Test
    void preservesPercentageJustBelowOneHundred() {
        String json = "{\"rate_limit\":{\"primary_window\":{\"used_percent\":99.96," 
                + "\"reset_at\":1792344925}}}";

        var result = new OpenAiUsageClient().parse(json, QUERY_TIME).orElseThrow();

        assertThat(result.reading().windows().get(0).usedPercentage()).isEqualTo(99.96);
    }

    @Test
    void usesQueryEndAndRelativeResetWhenAbsoluteValueIsAbsent() {
        String json = "{\"rate_limit\":{\"primary_window\":{\"used_percent\":2.4,"
                + "\"reset_after_seconds\":3600}}}";

        var result = new OpenAiUsageClient().parse(json, QUERY_TIME).orElseThrow();

        assertThat(result.reading().windows().get(0).resetsAt())
                .isEqualTo(QUERY_TIME.plusSeconds(3600));
    }

    @Test
    void invalidJsonOrMissingRateLimitIsInvalid() {
        OpenAiUsageClient client = new OpenAiUsageClient();

        assertThat(client.parse("not-json", QUERY_TIME)).isEmpty();
        assertThat(client.parse("{\"data\":{}}", QUERY_TIME)).isEmpty();
    }

    @Test
    void authenticatedRequestReturnsDataAndSendsOnlyRequiredHeaders() throws Exception {
        try (TestServer server = new TestServer(200, "{\"rate_limit\":{\"primary_window\":{"
                + "\"used_percent\":6,\"reset_after_seconds\":3600}}}")) {
            var result = new OpenAiUsageClient(HttpClient.newHttpClient(), server.uri())
                    .read(new OpenAiCredentials("sentinel-token", "sentinel-account"));

            assertThat(result.estado()).isEqualTo(es.vargontoc.subscriptionviewer.usage.UsageReadResult.Estado.DATOS);
            assertThat(server.authorization).isEqualTo("Bearer sentinel-token");
            assertThat(server.account).isEqualTo("sentinel-account");
            assertThat(server.accept).isEqualTo("application/json");
        }
    }

    @Test
    void unauthorizedResponsesBecomeExpiredSessionWithoutThrowing() throws Exception {
        for (int status : new int[]{401, 403}) {
            try (TestServer server = new TestServer(status, "{}")) {
                var result = new OpenAiUsageClient(HttpClient.newHttpClient(), server.uri())
                        .read(new OpenAiCredentials("token", "account"));
                assertThat(result.estado()).isEqualTo(
                        es.vargontoc.subscriptionviewer.usage.UsageReadResult.Estado.SESION_CADUCADA);
                assertThat(result.datos()).isEmpty();
            }
        }
    }

    @Test
    void otherStatusAndConnectionFailuresBecomeTransientFailure() throws Exception {
        try (TestServer server = new TestServer(500, "{}")) {
            var result = new OpenAiUsageClient(HttpClient.newHttpClient(), server.uri())
                    .read(new OpenAiCredentials("token", "account"));
            assertThat(result.estado()).isEqualTo(
                    es.vargontoc.subscriptionviewer.usage.UsageReadResult.Estado.FALLO_TRANSITORIO);
        }
        var unused = new HttpServerAddress();
        var result = new OpenAiUsageClient(HttpClient.newHttpClient(), unused.uri())
                .read(new OpenAiCredentials("token", "account"));
        assertThat(result.estado()).isEqualTo(
                es.vargontoc.subscriptionviewer.usage.UsageReadResult.Estado.FALLO_TRANSITORIO);
    }

    private static final class TestServer implements AutoCloseable {
        private final HttpServer server;
        private volatile String authorization;
        private volatile String account;
        private volatile String accept;

        private TestServer(int status, String body) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                authorization = exchange.getRequestHeaders().getFirst("Authorization");
                account = exchange.getRequestHeaders().getFirst("ChatGPT-Account-Id");
                accept = exchange.getRequestHeaders().getFirst("Accept");
                byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
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

    private static final class HttpServerAddress {
        private final int port;

        private HttpServerAddress() throws IOException {
            try (var socket = new java.net.ServerSocket(0)) {
                port = socket.getLocalPort();
            }
        }

        private URI uri() {
            return URI.create("http://127.0.0.1:" + port + "/");
        }
    }
}
