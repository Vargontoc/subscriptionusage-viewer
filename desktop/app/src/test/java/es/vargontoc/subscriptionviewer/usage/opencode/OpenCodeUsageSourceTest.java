package es.vargontoc.subscriptionviewer.usage.opencode;

import es.vargontoc.subscriptionviewer.usage.UsageReadResult;
import es.vargontoc.subscriptionviewer.usage.UsageWindowType;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import com.sun.net.httpserver.HttpServer;
import es.vargontoc.subscriptionviewer.config.ConfigurationProvider;
import es.vargontoc.subscriptionviewer.config.OpenCodeApiKey;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenCodeUsageSourceTest {
    private static final Instant FINISHED_AT = Instant.parse("2026-10-17T10:00:00Z");
    private final OpenCodeUsageSource source = new OpenCodeUsageSource(new ObjectMapper());

    @Test
    void normalizesPercentAndResetsAtVariant() {
        UsageReadResult result = source.normalize("""
                {"usage":{"rolling":{"percent":30,"resetsAt":"2026-10-17T15:00:00Z"}}}
                """, FINISHED_AT);

        assertThat(result.isPresent()).isTrue();
        var window = result.get().windows().getFirst();
        assertThat(window.type()).isEqualTo(UsageWindowType.ROLLING);
        assertThat(window.usedPercentage()).isEqualTo(30);
        assertThat(window.resetsAt()).isEqualTo(Instant.parse("2026-10-17T15:00:00Z"));
    }

    @Test
    void calculatesResetFromQueryCompletionForRelativeVariant() {
        UsageReadResult result = source.normalize("""
                {"usage":{"monthly":{"usagePercent":42,"resetInSec":7200}}}
                """, FINISHED_AT);

        var window = result.get().windows().getFirst();
        assertThat(window.type()).isEqualTo(UsageWindowType.MONTHLY);
        assertThat(window.usedPercentage()).isEqualTo(42);
        assertThat(window.resetsAt()).isEqualTo(FINISHED_AT.plusSeconds(7200));
    }

    @Test
    void omitsMissingWeeklyAndKeepsOtherWindows() {
        UsageReadResult result = source.normalize("""
                {"usage":{
                  "rolling":{"percent":10,"resetsAt":"2026-10-17T11:00:00Z"},
                  "monthly":{"usagePercent":20,"resetInSec":3600}
                },"newField":"ignored"}
                """, FINISHED_AT);

        assertThat(result.get().windows()).extracting(window -> window.type())
                .containsExactly(UsageWindowType.ROLLING, UsageWindowType.MONTHLY);
    }

    @Test
    void acceptsDifferentVariantInEachWindow() {
        UsageReadResult result = source.normalize("""
                {"usage":{
                  "rolling":{"percent":12,"resetsAt":"2026-10-17T12:00:00Z"},
                  "weekly":{"usagePercent":34,"resetInSec":1800},
                  "monthly":{"percent":56,"resetsAt":"2026-11-01T00:00:00Z"}
                }}
                """, FINISHED_AT);

        assertThat(result.get().windows()).hasSize(3);
        assertThat(result.get().windows().get(1).resetsAt()).isEqualTo(FINISHED_AT.plusSeconds(1800));
        assertThat(result.get().windows().get(2).usedPercentage()).isEqualTo(56);
    }

    @Test
    void omitsUnreadableWindowButProcessesRemainingWindows() {
        UsageReadResult result = source.normalize("""
                {"usage":{
                  "rolling":{"percent":"not-a-number","resetsAt":"2026-10-17T12:00:00Z"},
                  "weekly":{"usagePercent":34,"resetInSec":1800},
                  "monthly":{"percent":56,"resetsAt":"2026-11-01T00:00:00Z"}
                }}
                """, FINISHED_AT);

        assertThat(result.get().windows()).extracting(window -> window.type())
                .containsExactly(UsageWindowType.WEEKLY, UsageWindowType.MONTHLY);
    }

    @Disabled("v1 - retirar en TASK-021")
    @Test
    void readsHttpUsageWithBearerHeader() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        AtomicReference<String> authorization = new AtomicReference<>();
        server.createContext("/usage", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{\"usage\":{\"rolling\":{\"percent\":30,\"resetsAt\":\"2026-10-17T15:00:00Z\"}}}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            ConfigurationProvider configuration = configuration(server, "sentinel-key");
            UsageReadResult result = new OpenCodeUsageSource(new ObjectMapper(), configuration).read();
            assertThat(result.estado()).isEqualTo(UsageReadResult.Estado.DATOS);
            assertThat(authorization).hasValue("Bearer sentinel-key");
        } finally {
            server.stop(0);
        }
    }

    @Disabled("v1 - retirar en TASK-021")
    @Test
    void classifiesUnauthorizedAndForbiddenResponses() throws Exception {
        for (int status : new int[]{401, 403}) {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/usage", exchange -> {
                exchange.sendResponseHeaders(status, -1);
                exchange.close();
            });
            server.start();
            try {
                UsageReadResult result = new OpenCodeUsageSource(new ObjectMapper(),
                        configuration(server, "sentinel-key")).read();
                assertThat(result.estado()).isEqualTo(status == 401
                        ? UsageReadResult.Estado.CLAVE_INVALIDA
                        : UsageReadResult.Estado.SIN_SUSCRIPCION);
            } finally {
                server.stop(0);
            }
        }
    }

    @Disabled("v1 - retirar en TASK-021")
    @Test
    void doesNotRequestHttpWhenKeyIsAbsent() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/usage", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            ConfigurationProvider configuration = configuration(server, null);
            UsageReadResult result = new OpenCodeUsageSource(new ObjectMapper(), configuration).read();
            assertThat(result.estado()).isEqualTo(UsageReadResult.Estado.CLAVE_AUSENTE);
            assertThat(requests).hasValue(0);
        } finally {
            server.stop(0);
        }
    }

    private ConfigurationProvider configuration(HttpServer server, String key) {
        ConfigurationProvider configuration = mock(ConfigurationProvider.class);
        when(configuration.getOpenCodeUsageUrl()).thenReturn("http://localhost:" + server.getAddress().getPort() + "/usage");
        when(configuration.hasOpenCodeApiKey()).thenReturn(key != null);
        when(configuration.getOpenCodeApiKey()).thenReturn(key == null ? null : new OpenCodeApiKey(key));
        return configuration;
    }
}
