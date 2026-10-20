package es.vargontoc.subscriptionviewer.usage.gemini;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import es.vargontoc.subscriptionviewer.config.ConfigurationProvider;
import es.vargontoc.subscriptionviewer.usage.UsageReadResult;
import es.vargontoc.subscriptionviewer.usage.UsageReading;
import es.vargontoc.subscriptionviewer.usage.UsageSource;
import es.vargontoc.subscriptionviewer.usage.UsageWindowReading;

/** Fuente de lecturas de uso obtenidas mediante {@code agy}. */
@Component
public final class GeminiUsageSource implements UsageSource {
    private static final Logger log = LoggerFactory.getLogger(GeminiUsageSource.class);
    private static final String PROVIDER_ID = "gemini";
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(90);

    private final GeminiCliRunner runner;
    private final GeminiUsageParser parser;
    private final ConfigurationProvider configurationProvider;

    @Autowired
    public GeminiUsageSource(GeminiCliRunner runner, GeminiUsageParser parser,
            ConfigurationProvider configurationProvider) {
        this.runner = runner;
        this.parser = parser;
        this.configurationProvider = configurationProvider;
    }

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public Duration getPollInterval(ConfigurationProvider provider) {
        ConfigurationProvider effective = provider == null ? configurationProvider : provider;
        return effective.getGeminiUsagePollInterval();
    }

    @Override
    public Duration getReadTimeout() {
        return READ_TIMEOUT;
    }

    @Override
    public UsageReadResult read() {
        try {
            GeminiCliRunner.CliResult cliResult = runner.run();
            if (cliResult == null || !cliResult.isSuccess()) {
                return consultaFallida();
            }

            GeminiUsageParser.ParseResult parseResult = parser.parse(cliResult.output());
            if (parseResult == null || !parseResult.isValid()) {
                return consultaFallida();
            }
            return UsageReadResult.datos(construirLectura(parseResult));
        } catch (Exception ignored) {
            return consultaFallida();
        }
    }

    @Override
    public UsageReadResult doReadResult() {
        return read();
    }

    @Override
    public java.util.Optional<UsageReading> doRead() {
        return read().datos();
    }

    private UsageReadResult consultaFallida() {
        // Nunca se registran salida, argumentos, códigos ni motivos técnicos.
        log.debug("consulta-fallida");
        return UsageReadResult.consultaFallida();
    }

    private UsageReading construirLectura(GeminiUsageParser.ParseResult parseResult) {
        Instant now = Instant.now();
        List<UsageReading> seriesReadings = new ArrayList<>();
        for (GeminiGroup group : parseResult.groups()) {
            List<UsageWindowReading> windows = new ArrayList<>();
            for (GeminiWindow window : group.windows()) {
                windows.add(new UsageWindowReading(window.type(), window.percentage(), window.resetsAt()));
            }
            seriesReadings.add(new UsageReading(PROVIDER_ID, group.name(), now, windows));
        }
        return new UsageReading(PROVIDER_ID, now, List.of(), seriesReadings);
    }
}
