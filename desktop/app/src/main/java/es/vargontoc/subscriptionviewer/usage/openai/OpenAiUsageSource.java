package es.vargontoc.subscriptionviewer.usage.openai;

import es.vargontoc.subscriptionviewer.config.ConfigurationProvider;
import es.vargontoc.subscriptionviewer.usage.UsageReadResult;
import es.vargontoc.subscriptionviewer.usage.UsageSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/** Fuente de uso de OpenAI. Las credenciales se vuelven a leer en cada ciclo. */
@Component
public final class OpenAiUsageSource implements UsageSource {
    private static final String PROVIDER_ID = "openai";
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

    private final OpenAiCredentialsReader credentialsReader;
    private final OpenAiUsageClient usageClient;
    private final ConfigurationProvider configurationProvider;

    @Autowired
    public OpenAiUsageSource(OpenAiCredentialsReader credentialsReader,
            OpenAiUsageClient usageClient, ConfigurationProvider configurationProvider) {
        this.credentialsReader = credentialsReader;
        this.usageClient = usageClient;
        this.configurationProvider = configurationProvider;
    }

    /** Forma alternativa que sigue el orden habitual de configuración primero. */
    public OpenAiUsageSource(ConfigurationProvider configurationProvider,
            OpenAiCredentialsReader credentialsReader, OpenAiUsageClient usageClient) {
        this(credentialsReader, usageClient, configurationProvider);
    }

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public Duration getPollInterval(ConfigurationProvider provider) {
        ConfigurationProvider effective = provider == null ? configurationProvider : provider;
        return effective.getOpenAiUsagePollInterval();
    }

    @Override
    public Duration getReadTimeout() {
        return READ_TIMEOUT;
    }

    @Override
    public UsageReadResult read() {
        try {
            OpenAiCredentialsReadResult credentials = credentialsReader.read();
            if (credentials == null || credentials.credentials().isEmpty()) {
                return UsageReadResult.falloTransitorio(null);
            }
            UsageReadResult result = usageClient.read(credentials.credentials().orElseThrow());
            return result == null ? UsageReadResult.falloTransitorio(null) : result;
        } catch (Exception ignored) {
            // Una fuente nunca debe romper el planificador ni exponer datos sensibles.
            return UsageReadResult.falloTransitorio(null);
        }
    }

    @Override
    public UsageReadResult doReadResult() {
        return read();
    }

    @Override
    public Optional<es.vargontoc.subscriptionviewer.usage.UsageReading> doRead() {
        return read().datos();
    }
}
