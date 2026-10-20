package es.vargontoc.subscriptionviewer.usage.gemini;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import es.vargontoc.subscriptionviewer.config.ConfigurationProvider;
import es.vargontoc.subscriptionviewer.usage.UsageReadResult;
import es.vargontoc.subscriptionviewer.usage.UsageWindowType;

class GeminiUsageSourceTest {
    private final GeminiCliRunner runner = mock(GeminiCliRunner.class);
    private final GeminiUsageParser parser = mock(GeminiUsageParser.class);
    private final ConfigurationProvider configuration = mock(ConfigurationProvider.class);
    private final GeminiUsageSource source = new GeminiUsageSource(runner, parser, configuration);

    @Test
    void devuelveDatosConUnaSeriePorGrupo() {
        GeminiGroup first = new GeminiGroup("Gemini Models",
                List.of(new GeminiWindow(UsageWindowType.WEEKLY, 12.5, Instant.parse("2026-10-26T00:00:00Z"))));
        GeminiGroup second = new GeminiGroup("Argon", List.of());
        when(runner.run()).thenReturn(new GeminiCliRunner.CliResult(true, "{\"sentinel\":true}"));
        when(parser.parse("{\"sentinel\":true}"))
                .thenReturn(GeminiUsageParser.ParseResult.valid(List.of(first, second)));

        UsageReadResult result = source.read();

        assertEquals(UsageReadResult.Estado.DATOS, result.estado());
        assertEquals(2, result.get().seriesReadings().size());
        assertEquals("Gemini Models", result.get().seriesReadings().get(0).seriesId());
        assertEquals(1, result.get().seriesReadings().get(0).windows().size());
    }

    @Test
    void ejecutableInexistenteDevuelveConsultaFallida() {
        when(runner.run()).thenReturn(new GeminiCliRunner.CliResult(false, ""));
        assertConsultaFallida(source.read());
    }

    @Test
    void codigoDistintoDeCeroDevuelveConsultaFallida() {
        when(runner.run()).thenReturn(new GeminiCliRunner.CliResult(false, ""));
        assertConsultaFallida(source.read());
    }

    @Test
    void tiempoAgotadoDevuelveConsultaFallida() {
        when(runner.run()).thenThrow(new RuntimeException("timeout"));
        assertConsultaFallida(source.read());
    }

    @Test
    void parserInvalidoDevuelveConsultaFallida() {
        when(runner.run()).thenReturn(new GeminiCliRunner.CliResult(true, "centinela"));
        when(parser.parse("centinela")).thenReturn(GeminiUsageParser.ParseResult.invalid("invalido"));
        assertConsultaFallida(source.read());
    }

    @Test
    void declaraLosTiemposDeGemini() {
        when(configuration.getGeminiUsagePollInterval()).thenReturn(Duration.ofSeconds(45));
        assertEquals(Duration.ofSeconds(90), source.getReadTimeout());
        assertEquals(Duration.ofSeconds(45), source.getPollInterval(null));
    }

    private static void assertConsultaFallida(UsageReadResult result) {
        assertEquals(UsageReadResult.Estado.CONSULTA_FALLIDA, result.estado());
        assertFalse(result.datos().isPresent());
        assertTrue(result.causa().isEmpty());
    }
}
