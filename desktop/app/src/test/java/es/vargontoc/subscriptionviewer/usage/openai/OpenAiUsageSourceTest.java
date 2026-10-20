package es.vargontoc.subscriptionviewer.usage.openai;

import es.vargontoc.subscriptionviewer.config.ConfigurationProvider;
import es.vargontoc.subscriptionviewer.usage.UsageReadResult;
import es.vargontoc.subscriptionviewer.usage.UsageWindowType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OpenAiUsageSourceTest {
    @Test
    void credencialesYRespuestaValidaProducenDatos() {
        OpenAiCredentialsReader reader = mock(OpenAiCredentialsReader.class);
        OpenAiUsageClient client = mock(OpenAiUsageClient.class);
        ConfigurationProvider configuration = mock(ConfigurationProvider.class);
        OpenAiCredentials credentials = new OpenAiCredentials("token", "account");
        var reading = new es.vargontoc.subscriptionviewer.usage.UsageReading("openai",
                Instant.parse("2026-10-19T12:00:00Z"), List.of(
                        new es.vargontoc.subscriptionviewer.usage.UsageWindowReading(
                                UsageWindowType.FIVE_HOUR, 6, Instant.parse("2026-10-19T17:00:00Z")),
                        new es.vargontoc.subscriptionviewer.usage.UsageWindowReading(
                                UsageWindowType.SEVEN_DAY, 1, Instant.parse("2026-10-26T12:00:00Z"))),
                null, List.of(), "plus");
        when(reader.read()).thenReturn(new OpenAiCredentialsReadResult(true, credentials));
        when(client.read(credentials)).thenReturn(UsageReadResult.datos(reading));

        UsageReadResult result = new OpenAiUsageSource(reader, client, configuration).read();

        assertThat(result.estado()).isEqualTo(UsageReadResult.Estado.DATOS);
        assertThat(result.get().planType()).isEqualTo("plus");
        assertThat(result.get().windows()).extracting(w -> w.usedPercentage()).containsExactly(6.0, 1.0);
        verify(client).read(credentials);
    }

    @Test
    void sinCredencialesNoHacePeticionYDevuelveFalloSinCausa() {
        OpenAiCredentialsReader reader = mock(OpenAiCredentialsReader.class);
        OpenAiUsageClient client = mock(OpenAiUsageClient.class);
        when(reader.read()).thenReturn(new OpenAiCredentialsReadResult(false, null));

        UsageReadResult result = new OpenAiUsageSource(reader, client, mock(ConfigurationProvider.class)).read();

        assertThat(result.estado()).isEqualTo(UsageReadResult.Estado.FALLO_TRANSITORIO);
        assertThat(result.causa()).isEmpty();
        verifyNoInteractions(client);
    }

    @Test
    void respuestaNoValidaYCaidaDeRedSeDevuelvenClasificadasSinExcepcion() {
        OpenAiCredentialsReader reader = mock(OpenAiCredentialsReader.class);
        OpenAiUsageClient client = mock(OpenAiUsageClient.class);
        OpenAiCredentials credentials = new OpenAiCredentials("token", "account");
        when(reader.read()).thenReturn(new OpenAiCredentialsReadResult(true, credentials));
        when(client.read(credentials)).thenReturn(UsageReadResult.sesionCaducada())
                .thenReturn(UsageReadResult.falloTransitorio(null));
        OpenAiUsageSource source = new OpenAiUsageSource(reader, client, mock(ConfigurationProvider.class));

        assertThat(source.read().estado()).isEqualTo(UsageReadResult.Estado.SESION_CADUCADA);
        assertThat(source.read().estado()).isEqualTo(UsageReadResult.Estado.FALLO_TRANSITORIO);
    }

    @Test
    void declaraIntervaloConfiguradoYTimeoutDeSesentaSegundos() {
        ConfigurationProvider configuration = mock(ConfigurationProvider.class);
        when(configuration.getOpenAiUsagePollInterval()).thenReturn(Duration.ofSeconds(30));
        OpenAiUsageSource source = new OpenAiUsageSource(mock(OpenAiCredentialsReader.class),
                mock(OpenAiUsageClient.class), configuration);

        assertThat(source.getPollInterval(configuration)).isEqualTo(Duration.ofSeconds(30));
        assertThat(source.getReadTimeout()).isEqualTo(Duration.ofSeconds(60));
    }
}
