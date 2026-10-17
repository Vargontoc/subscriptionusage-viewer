package es.vargontoc.subscriptionviewer.usage.opencode;

import es.vargontoc.subscriptionviewer.config.ConfigurationProvider;
import es.vargontoc.subscriptionviewer.usage.UsageReadResult;
import es.vargontoc.subscriptionviewer.usage.UsageReading;
import es.vargontoc.subscriptionviewer.usage.UsageWindowReading;
import es.vargontoc.subscriptionviewer.usage.UsageWindowType;
import es.vargontoc.subscriptionviewer.usage.UsageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
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

/** Normaliza la respuesta JSON de uso de OpenCode al modelo común de uso. */
@Component
public class OpenCodeUsageSource implements UsageSource {
    private static final String PROVIDER_ID = "opencode";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Logger log = LoggerFactory.getLogger(OpenCodeUsageSource.class);

    private final ObjectMapper objectMapper;
    private final ConfigurationProvider configurationProvider;
    private final HttpClient httpClient;

    public OpenCodeUsageSource(ObjectMapper objectMapper) {
        this(objectMapper, null, HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    @Autowired
    public OpenCodeUsageSource(ObjectMapper objectMapper, ConfigurationProvider configurationProvider) {
        this(objectMapper, configurationProvider, HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    public OpenCodeUsageSource(ConfigurationProvider configurationProvider, ObjectMapper objectMapper) {
        this(objectMapper, configurationProvider);
    }

    /** Constructor intended for deterministic tests; the client still enforces the request timeout. */
    public OpenCodeUsageSource(ObjectMapper objectMapper, ConfigurationProvider configurationProvider,
                               HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.configurationProvider = configurationProvider;
        this.httpClient = httpClient;
    }

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    /** Consulta el endpoint configurado y nunca propaga fallos de red al llamador. */
    @Override
    public UsageReadResult read() {
        if (configurationProvider == null || !configurationProvider.hasOpenCodeApiKey()) {
            return UsageReadResult.claveAusente();
        }

        int status = 0;
        try {
            URI uri = URI.create(configurationProvider.getOpenCodeUsageUrl());
            String key = configurationProvider.getOpenCodeApiKey().value();
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Authorization", "Bearer " + key)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            status = response.statusCode();
            if (status == 200) {
                UsageReadResult result = normalize(response.body(), Instant.now());
                if (result.estado() == UsageReadResult.Estado.FALLO_TRANSITORIO) {
                    logFailure("fallo-transitorio", status);
                }
                return result;
            }
            if (status == 401) {
                logFailure("clave-invalida", status);
                return UsageReadResult.claveInvalida();
            }
            if (status == 403) {
                logFailure("sin-suscripcion", status);
                return UsageReadResult.sinSuscripcion();
            }
            logFailure("fallo-transitorio", status);
            return UsageReadResult.falloTransitorio(null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logFailure("fallo-transitorio", status);
            return UsageReadResult.falloTransitorio(null);
        } catch (IOException | RuntimeException exception) {
            // No se registra la excepción: algunas implementaciones incluyen la URI o cabeceras.
            logFailure("fallo-transitorio", status);
            return UsageReadResult.falloTransitorio(null);
        }
    }

    @Override
    public Optional<UsageReading> doRead() {
        return read().datos();
    }

    private void logFailure(String category, int status) {
        log.warn("OpenCode usage failure category={} status={}", category, status);
    }

    /**
     * Procesa una respuesta ya obtenida por HTTP. La hora recibida es la de
     * finalización de la consulta y se usa como origen de los reinicios relativos.
     * Las propiedades desconocidas se ignoran al leer mapas y cada ventana se
     * valida de forma independiente.
     */
    public UsageReadResult normalize(String response, Instant queryFinishedAt) {
        try {
            Map<?, ?> root = objectMapper.readValue(response, Map.class);
            Object usageValue = root.get("usage");
            if (!(usageValue instanceof Map<?, ?> usage)) {
                return UsageReadResult.datos(new UsageReading(PROVIDER_ID, queryFinishedAt, List.of()));
            }

            List<UsageWindowReading> windows = new ArrayList<>();
            addWindow(usage, "rolling", UsageWindowType.ROLLING, queryFinishedAt, windows);
            addWindow(usage, "weekly", UsageWindowType.WEEKLY, queryFinishedAt, windows);
            addWindow(usage, "monthly", UsageWindowType.MONTHLY, queryFinishedAt, windows);
            return UsageReadResult.datos(new UsageReading(PROVIDER_ID, queryFinishedAt, windows));
        } catch (Exception exception) {
            // No conservamos la excepción del parser: podría incluir el cuerpo recibido.
            return UsageReadResult.falloTransitorio(null);
        }
    }

    private void addWindow(Map<?, ?> usage, String name, UsageWindowType type,
                           Instant queryFinishedAt, List<UsageWindowReading> target) {
        Object value = usage.get(name);
        if (!(value instanceof Map<?, ?> window)) {
            return;
        }

        UsageWindowReading reading = fromAbsoluteVariant(window, type);
        if (reading == null) {
            reading = fromRelativeVariant(window, type, queryFinishedAt);
        }
        if (reading != null) {
            target.add(reading);
        }
    }

    private UsageWindowReading fromAbsoluteVariant(Map<?, ?> window, UsageWindowType type) {
        Object percent = window.get("percent");
        Object resetsAt = window.get("resetsAt");
        if (!(percent instanceof Number) || !(resetsAt instanceof String)) {
            return null;
        }
        Integer normalizedPercent = percentage(percent);
        if (normalizedPercent == null) {
            return null;
        }
        try {
            return new UsageWindowReading(type, normalizedPercent, Instant.parse((String) resetsAt));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private UsageWindowReading fromRelativeVariant(Map<?, ?> window, UsageWindowType type,
                                                    Instant queryFinishedAt) {
        Object percent = window.get("usagePercent");
        Object resetInSec = window.get("resetInSec");
        if (!(percent instanceof Number) || !(resetInSec instanceof Number)
                || queryFinishedAt == null) {
            return null;
        }
        Integer normalizedPercent = percentage(percent);
        if (normalizedPercent == null) {
            return null;
        }
        double seconds = ((Number) resetInSec).doubleValue();
        if (!Double.isFinite(seconds) || seconds < 0 || seconds > Long.MAX_VALUE) {
            return null;
        }
        try {
            long wholeSeconds = Math.round(seconds);
            return new UsageWindowReading(type, normalizedPercent,
                    queryFinishedAt.plusSeconds(wholeSeconds));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Integer percentage(Object value) {
        double percentage = ((Number) value).doubleValue();
        if (!Double.isFinite(percentage) || percentage < 0 || percentage > 100) {
            return null;
        }
        return (int) Math.round(percentage);
    }
}
