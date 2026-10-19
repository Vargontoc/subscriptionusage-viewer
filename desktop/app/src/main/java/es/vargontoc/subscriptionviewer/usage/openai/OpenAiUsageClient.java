package es.vargontoc.subscriptionviewer.usage.openai;

import es.vargontoc.subscriptionviewer.usage.UsageReading;
import es.vargontoc.subscriptionviewer.usage.UsageReadResult;
import es.vargontoc.subscriptionviewer.usage.UsageWindowReading;
import es.vargontoc.subscriptionviewer.usage.UsageWindowType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Cliente de cuota de OpenAI y parser de su respuesta. */
@Component
public final class OpenAiUsageClient {
    private static final String PROVIDER_ID = "openai";
    private static final URI USAGE_URI = URI.create("https://chatgpt.com/backend-api/wham/usage");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final URI usageUri;

    public OpenAiUsageClient() {
        this(HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build(), USAGE_URI, new ObjectMapper());
    }

    public OpenAiUsageClient(ObjectMapper objectMapper) {
        this(HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build(), USAGE_URI, objectMapper);
    }

    /** Constructor que permite probar el cliente contra un servidor controlado. */
    public OpenAiUsageClient(HttpClient httpClient, URI usageUri) {
        this(httpClient, usageUri, new ObjectMapper());
    }

    public OpenAiUsageClient(HttpClient httpClient, URI usageUri, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.usageUri = usageUri;
        this.objectMapper = objectMapper;
    }

    /**
     * Consulta el endpoint sin propagar errores de red, HTTP ni de parseo al
     * llamador. La operación HTTP se inicia con {@code sendAsync}, por lo que
     * el trabajo de red no se ejecuta en el hilo de interfaz.
     */
    public UsageReadResult read(OpenAiCredentials credentials) {
        try {
            return fetchAsync(credentials).join();
        } catch (RuntimeException ignored) {
            return UsageReadResult.falloTransitorio(null);
        }
    }

    /** Variante asíncrona para que el planificador pueda encadenar la lectura. */
    public CompletableFuture<UsageReadResult> fetchAsync(OpenAiCredentials credentials) {
        if (credentials == null) {
            return CompletableFuture.completedFuture(UsageReadResult.falloTransitorio(null));
        }
        final HttpRequest request;
        try {
            request = HttpRequest.newBuilder(usageUri)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Authorization", "Bearer " + credentials.accessTokenForHeader())
                    .header("ChatGPT-Account-Id", credentials.accountIdForHeader())
                    .header("Accept", "application/json")
                    .GET()
                    .build();
        } catch (RuntimeException ignored) {
            return CompletableFuture.completedFuture(UsageReadResult.falloTransitorio(null));
        }

        try {
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .orTimeout(REQUEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                    .handle((response, failure) -> {
                    if (failure != null || response == null) {
                        return UsageReadResult.falloTransitorio(null);
                    }
                    int status = response.statusCode();
                    if (status == 401 || status == 403) {
                        return UsageReadResult.sesionCaducada();
                    }
                    if (status != 200) {
                        return UsageReadResult.falloTransitorio(null);
                    }
                    return parse(response.body(), Instant.now())
                            .<UsageReadResult>map(result -> UsageReadResult.datos(result.reading()))
                            .orElseGet(() -> UsageReadResult.falloTransitorio(null));
                    });
        } catch (RuntimeException ignored) {
            return CompletableFuture.completedFuture(UsageReadResult.falloTransitorio(null));
        }
    }

    /**
     * Interpreta el cuerpo usando como hora de consulta la hora actual.
     * La hora explícita debe preferirse en producción para que sea la de fin
     * de la petición.
     */
    public Optional<OpenAiUsageResult> parse(String json) {
        return parse(json, Instant.now());
    }

    /** Interpreta el cuerpo y usa {@code queriedAt} para el reinicio relativo. */
    public Optional<OpenAiUsageResult> parse(String json, Instant queriedAt) {
        if (json == null || queriedAt == null) {
            return Optional.empty();
        }
        try {
            Object decoded = objectMapper.readValue(json, Map.class);
            if (!(decoded instanceof Map<?, ?> root)
                    || !(root.get("rate_limit") instanceof Map<?, ?> rateLimit)
                    || !(rateLimit.get("primary_window") instanceof Map<?, ?> primary)) {
                return Optional.empty();
            }

            UsageWindowReading primaryReading = window(primary, UsageWindowType.FIVE_HOUR, queriedAt);
            if (primaryReading == null) {
                return Optional.empty();
            }

            List<UsageWindowReading> windows = new ArrayList<>();
            windows.add(primaryReading);
            if (rateLimit.get("secondary_window") instanceof Map<?, ?> secondary) {
                UsageWindowReading secondaryReading = window(secondary, UsageWindowType.SEVEN_DAY, queriedAt);
                if (secondaryReading == null) {
                    return Optional.empty();
                }
                windows.add(secondaryReading);
            }

            String planType = root.get("plan_type") instanceof String plan && !plan.isBlank()
                    ? plan : null;
            return Optional.of(new OpenAiUsageResult(
                    new UsageReading(PROVIDER_ID, queriedAt, windows), planType));
        } catch (RuntimeException ignored) {
            // El cuerpo puede contener información de sesión: no se conserva ni se propaga.
            return Optional.empty();
        }
    }

    public Optional<OpenAiUsageResult> parseResponse(String json, Instant queriedAt) {
        return parse(json, queriedAt);
    }

    private UsageWindowReading window(Map<?, ?> values, UsageWindowType type, Instant queriedAt) {
        Object percentageValue = values.get("used_percent");
        if (!(percentageValue instanceof Number number)) {
            return null;
        }
        double percentage = number.doubleValue();
        if (!Double.isFinite(percentage) || percentage < 0 || percentage > 100) {
            return null;
        }

        Instant reset = resetAt(values);
        if (reset == null && values.get("reset_after_seconds") instanceof Number seconds
                && Double.isFinite(seconds.doubleValue()) && seconds.doubleValue() >= 0) {
            reset = queriedAt.plusSeconds(seconds.longValue());
        }
        if (reset == null) {
            return null;
        }
        return new UsageWindowReading(type, (int) Math.round(percentage), reset);
    }

    private Instant resetAt(Map<?, ?> values) {
        Object value = values.get("reset_at");
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            return null;
        }
        try {
            long epochSeconds = number.longValue();
            return epochSeconds > 0 ? Instant.ofEpochSecond(epochSeconds) : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
