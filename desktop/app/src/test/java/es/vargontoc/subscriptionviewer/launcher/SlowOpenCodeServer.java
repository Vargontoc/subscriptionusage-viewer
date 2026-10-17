package es.vargontoc.subscriptionviewer.launcher;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/** Servidor local de uso que permite reproducir una respuesta lenta sin red externa. */
final class SlowOpenCodeServer implements AutoCloseable {

    private static final String RESPONSE = "{\"usage\":{\"rolling\":{\"percent\":30,"
            + "\"resetsAt\":\"2099-01-01T00:00:00Z\"}}}";

    private final HttpServer server;
    private final long delayMillis;
    private final AtomicInteger requests = new AtomicInteger();

    SlowOpenCodeServer(long delayMillis) throws IOException {
        this.delayMillis = delayMillis;
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/usage", exchange -> {
            requests.incrementAndGet();
            try {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    exchange.close();
                    return;
                }
                byte[] body = RESPONSE.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/usage";
    }

    int requests() {
        return requests.get();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
