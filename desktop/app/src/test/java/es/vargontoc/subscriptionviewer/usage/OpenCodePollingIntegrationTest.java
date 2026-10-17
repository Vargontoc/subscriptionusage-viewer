package es.vargontoc.subscriptionviewer.usage;

import com.sun.net.httpserver.HttpServer;
import es.vargontoc.subscriptionviewer.config.ConfigurationProvider;
import es.vargontoc.subscriptionviewer.config.OpenCodeApiKey;
import es.vargontoc.subscriptionviewer.usage.opencode.OpenCodeUsageSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Verifica el ensamblaje de la fuente HTTP con el planificador. */
class OpenCodePollingIntegrationTest {
    private final List<HttpServer> servers = new ArrayList<>();

    @AfterEach
    void stopServers() {
        servers.forEach(server -> server.stop(0));
    }

    @Test
    void successfulReadReachesHistoryAndPublisher() throws Exception {
        HttpServer server = server(200, "{\"usage\":{\"rolling\":{\"percent\":30,\"resetsAt\":\"2030-01-01T00:00:00Z\"}}}");
        ConfigurationProvider config = config(url(server));
        UsageHistory history = new UsageHistory();
        UsageSnapshotPublisher publisher = new UsageSnapshotPublisher(Runnable::run);
        List<Runnable> scheduled = new ArrayList<>();
        ScheduledExecutorService executor = executor(scheduled);
        UsagePoller poller = new UsagePoller(config,
                List.of(new OpenCodeUsageSource(new ObjectMapper(), config)), history, publisher,
                Clock.systemUTC(), executor);

        poller.start();
        scheduled.get(0).run();
        await(() -> publisher.getCurrentSnapshot().providerStates().containsKey("opencode"));

        assertThat(history.getSeries("opencode", UsageWindowType.ROLLING)).hasSize(1);
        assertThat(publisher.getCurrentSnapshot().providerStates().get("opencode").estado())
                .isEqualTo(UsageProviderState.Estado.CORRECTO);
    }

    @Test
    void unauthorizedReadPausesWithoutRetry() throws Exception {
        HttpServer server = server(401, "");
        ConfigurationProvider config = config(url(server));
        List<Runnable> scheduled = new ArrayList<>();
        UsagePoller poller = new UsagePoller(config, List.of(new OpenCodeUsageSource(new ObjectMapper(), config)),
                new UsageHistory(), new UsageSnapshotPublisher(Runnable::run), Clock.systemUTC(), executor(scheduled));

        poller.start();
        scheduled.get(0).run();
        await(() -> !poller.isRunning() || scheduled.size() == 1);
        assertThat(scheduled).hasSize(1);
    }

    @Test
    void transientNetworkFailureSchedulesBackoff() throws Exception {
        ConfigurationProvider config = config("http://127.0.0.1:1/usage");
        List<Runnable> scheduled = new ArrayList<>();
        List<Long> delays = new ArrayList<>();
        ScheduledExecutorService executor = executor(scheduled, delays);
        UsagePoller poller = new UsagePoller(config, List.of(new OpenCodeUsageSource(new ObjectMapper(), config)),
                new UsageHistory(), new UsageSnapshotPublisher(Runnable::run), Clock.systemUTC(), executor);

        poller.start();
        scheduled.get(0).run();
        await(() -> scheduled.size() == 2);
        assertThat(delays.get(1)).isEqualTo(60_000L);
    }

    @Test
    void absentKeyPublishesWithoutSchedulingOrRequesting() {
        ConfigurationProvider config = mock(ConfigurationProvider.class);
        when(config.hasOpenCodeApiKey()).thenReturn(false);
        when(config.getOpenCodeUsagePollInterval()).thenReturn(Duration.ofSeconds(60));
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        UsageSnapshotPublisher publisher = new UsageSnapshotPublisher(Runnable::run);
        OpenCodeUsageSource source = mock(OpenCodeUsageSource.class);
        when(source.getProviderId()).thenReturn("opencode");
        UsagePoller poller = new UsagePoller(config, List.of(source),
                new UsageHistory(), publisher, Clock.systemUTC(), executor);

        poller.start();

        assertThat(publisher.getCurrentSnapshot().providerStates().get("opencode").estado())
                .isEqualTo(UsageProviderState.Estado.CLAVE_AUSENTE);
    }

    private HttpServer server(int status, String body) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/usage", exchange -> {
            exchange.sendResponseHeaders(status, body.length());
            exchange.getResponseBody().write(body.getBytes());
            exchange.close();
        });
        server.start();
        servers.add(server);
        return server;
    }

    private String url(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/usage";
    }

    private ConfigurationProvider config(String url) {
        ConfigurationProvider config = mock(ConfigurationProvider.class);
        when(config.hasOpenCodeApiKey()).thenReturn(true);
        when(config.getOpenCodeApiKey()).thenReturn(new OpenCodeApiKey("test-key"));
        when(config.getOpenCodeUsageUrl()).thenReturn(url);
        when(config.getOpenCodeUsagePollInterval()).thenReturn(Duration.ofSeconds(60));
        when(config.getOpenCodeBackoffMaxInterval()).thenReturn(Duration.ofSeconds(300));
        return config;
    }

    private ScheduledExecutorService executor(List<Runnable> scheduled) {
        return executor(scheduled, new ArrayList<>());
    }

    private ScheduledExecutorService executor(List<Runnable> scheduled, List<Long> delays) {
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        when(executor.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS))).thenAnswer(invocation -> {
            scheduled.add(invocation.getArgument(0));
            delays.add(invocation.getArgument(1));
            return mock(ScheduledFuture.class);
        });
        return executor;
    }

    private void await(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }
}
