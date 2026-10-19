package es.vargontoc.subscriptionviewer.launcher;

import com.sun.net.httpserver.HttpServer;
import es.vargontoc.subscriptionviewer.usage.UsageReadResult;
import es.vargontoc.subscriptionviewer.usage.openai.OpenAiCredentials;
import es.vargontoc.subscriptionviewer.usage.openai.OpenAiUsageClient;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Comprueba que una consulta lenta de OpenAI no bloquea el hilo de Swing. */
class OpenAiResourceAndResponsivenessIntegrationTest {

    private static final String RESPONSE = "{\"rate_limit\":{"
            + "\"primary_window\":{\"used_percent\":6,\"reset_after_seconds\":3600}}}";

    @Test
    void slowOpenAiQueryLeavesEdtResponsive() throws Exception {
        try (SlowUsageServer server = new SlowUsageServer()) {
            OpenAiUsageClient client = new OpenAiUsageClient(HttpClient.newHttpClient(), server.uri());
            CompletableFuture<UsageReadResult> pending = client.fetchAsync(
                    new OpenAiCredentials("test-access-token", "test-account"));

            assertThat(server.requestStarted.await(2, TimeUnit.SECONDS))
                    .as("la consulta lenta debe estar en curso").isTrue();

            long started = System.nanoTime();
            SwingUtilities.invokeAndWait(() -> { /* simula una interacción de la interfaz */ });
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

            assertThat(elapsedMillis).as("el EDT no debe esperar a OpenAI")
                    .isLessThan(1_000);
            assertThat(pending).as("la respuesta externa debe seguir pendiente")
                    .isNotDone();
            assertThat(pending.get(8, TimeUnit.SECONDS).estado())
                    .isEqualTo(UsageReadResult.Estado.DATOS);
        }
    }

    /** Servidor determinista: mantiene la respuesta abierta seis segundos. */
    private static final class SlowUsageServer implements AutoCloseable {
        private final HttpServer server;
        private final CountDownLatch requestStarted = new CountDownLatch(1);

        private SlowUsageServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/usage", exchange -> {
                requestStarted.countDown();
                try {
                    Thread.sleep(6_000);
                    byte[] body = RESPONSE.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, body.length);
                    try (var output = exchange.getResponseBody()) {
                        output.write(body);
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } finally {
                    exchange.close();
                }
            });
            server.start();
        }

        private URI uri() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/usage");
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
