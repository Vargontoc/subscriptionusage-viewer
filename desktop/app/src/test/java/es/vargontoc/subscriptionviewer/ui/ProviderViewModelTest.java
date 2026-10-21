package es.vargontoc.subscriptionviewer.ui;

import es.vargontoc.subscriptionviewer.usage.CountdownEvaluator;
import es.vargontoc.subscriptionviewer.usage.UsageProviderState;
import es.vargontoc.subscriptionviewer.usage.UsageReading;
import es.vargontoc.subscriptionviewer.usage.UsageSnapshot;
import es.vargontoc.subscriptionviewer.usage.UsageWindowReading;
import es.vargontoc.subscriptionviewer.usage.UsageWindowType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProviderViewModelTest {
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final CountdownEvaluator EVALUATOR = new CountdownEvaluator(CLOCK);

    @Test
    void estadoCorrectoSinVentanasClaudeDerivaErrorDeVigencia() {
        Instant updated = Instant.parse("2026-01-01T11:59:00Z");
        UsageReading reading = new UsageReading("claude", updated, List.of());
        UsageProviderState state = new UsageProviderState(
                UsageProviderState.Estado.CORRECTO, reading, updated);
        ProviderViewModel model = ProviderViewModel.from(
                "claude", new UsageSnapshot(Map.of(), Map.of("claude", state)), CLOCK, EVALUATOR);

        assertEquals(ProviderViewModel.PresentationState.ERROR, model.state());
        assertEquals(null, model.updatedAt());
        assertEquals(List.of(), model.tables());
    }

    @Test
    void ausenciaDeEntradaEsPrimeraConsulta() {
        ProviderViewModel model = ProviderViewModel.from(
                "gemini", new UsageSnapshot(), CLOCK, EVALUATOR);

        assertEquals(ProviderViewModel.PresentationState.FETCHING, model.state());
        assertEquals(List.of(), model.tables());
        assertEquals(null, model.updatedAt());
    }

    @Test
    void falloNoExponeLaLecturaAnteriorNiSuHora() {
        Instant updated = Instant.parse("2026-01-01T11:00:00Z");
        UsageReading reading = new UsageReading("openai", updated, List.of());
        UsageProviderState state = new UsageProviderState(
                UsageProviderState.Estado.CONSULTA_FALLIDA, reading, updated);
        ProviderViewModel model = ProviderViewModel.from(
                "openai", new UsageSnapshot(Map.of(), Map.of("openai", state)), CLOCK, EVALUATOR);

        assertEquals(ProviderViewModel.PresentationState.ERROR, model.state());
        assertEquals(null, model.updatedAt());
        assertEquals(List.of(), model.tables());
    }

    @Test
    void recordsSonInmutablesYComparablesPorValor() {
        List<ProviderViewModel.TableViewModel> tables = new ArrayList<>();
        ProviderViewModel first = new ProviderViewModel(
                "claude", ProviderViewModel.PresentationState.DATA, NOW, tables);
        tables.add(new ProviderViewModel.TableViewModel("Uso", List.of()));
        ProviderViewModel second = new ProviderViewModel(
                "claude", ProviderViewModel.PresentationState.DATA, NOW, List.of());

        assertEquals(second, first);
        assertEquals(List.of(), first.tables());
        assertThrows(UnsupportedOperationException.class,
                () -> first.tables().add(new ProviderViewModel.TableViewModel("x", List.of())));
    }

    @Test
    void claudeConAmbasVentanasVigentesConservaAmbasTablas() {
        UsageReading reading = reading("claude", future(1), future(2));
        ProviderViewModel model = model("claude", reading);

        assertEquals(ProviderViewModel.PresentationState.DATA, model.state());
        assertEquals(2, model.tables().size());
    }

    @Test
    void claudeOmiteLaVentanaCaducadaYConservaLaSemanal() {
        UsageReading reading = new UsageReading("claude", NOW, List.of(
                new UsageWindowReading(UsageWindowType.FIVE_HOUR, 40, NOW.minusSeconds(1)),
                new UsageWindowReading(UsageWindowType.SEVEN_DAY, 20, NOW.plusSeconds(60))));
        ProviderViewModel model = model("claude", reading);

        assertEquals(ProviderViewModel.PresentationState.DATA, model.state());
        assertEquals(List.of("Uso semanal"), model.tables().stream()
                .map(ProviderViewModel.TableViewModel::title).toList());
    }

    @Test
    void ventanasClaudeCaducadasDerivanErrorFormal() {
        ProviderViewModel model = model("claude", reading("claude", past(1), past(2)));

        assertEquals(ProviderViewModel.PresentationState.ERROR, model.state());
        assertEquals(ProviderErrorTexts.errorText("claude", UsageProviderState.Estado.SIN_DATOS),
                ProviderErrorTexts.errorText("claude", UsageProviderState.Estado.SIN_DATOS));
    }

    @Test
    void ventanasOpenAiCaducadasDerivanErrorDeConexionYSesion() {
        ProviderViewModel model = model("openai", reading("openai", past(1), past(2)));

        assertEquals(ProviderViewModel.PresentationState.ERROR, model.state());
        assertEquals(ProviderErrorTexts.errorText("openai", UsageProviderState.Estado.CONSULTA_FALLIDA),
                ProviderErrorTexts.errorText("openai", UsageProviderState.Estado.CONSULTA_FALLIDA));
    }

    private static ProviderViewModel model(String provider, UsageReading reading) {
        UsageProviderState state = new UsageProviderState(
                UsageProviderState.Estado.CORRECTO, reading, reading.writtenAt());
        return ProviderViewModel.from(provider,
                new UsageSnapshot(Map.of(), Map.of(provider, state)), CLOCK, EVALUATOR);
    }

    private static UsageReading reading(String provider, Instant first, Instant second) {
        return new UsageReading(provider, NOW, List.of(
                new UsageWindowReading(UsageWindowType.FIVE_HOUR, 40, first),
                new UsageWindowReading(UsageWindowType.SEVEN_DAY, 20, second)));
    }

    private static Instant future(long hours) { return NOW.plusSeconds(hours * 3600); }
    private static Instant past(long hours) { return NOW.minusSeconds(hours * 3600); }
}
