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
        assertEquals(List.of("Uso 5h", "Reinicia en"), model.tables().get(0).headers());
        assertEquals(List.of("Uso semanal", "Reinicia en"), model.tables().get(1).headers());
        assertEquals(40.0, model.tables().get(0).rows().get(0).percentageCell().percentage());
        assertEquals("1h 0m", model.tables().get(0).rows().get(0).timeCell().text());
        assertEquals("2h 0m", model.tables().get(1).rows().get(0).timeCell().text());
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

    @Test
    void openAiSinVentanaSemanalSoloConstruyeTablaDeCincoHoras() {
        ProviderViewModel model = model("openai", new UsageReading("openai", NOW,
                List.of(new UsageWindowReading(UsageWindowType.FIVE_HOUR, 40.0,
                        future(1)))));

        assertEquals(List.of("Uso 5h"), model.tables().stream()
                .map(ProviderViewModel.TableViewModel::title).toList());
    }

    @Test
    void openAiNoExponePlanTypeEnElModelo() {
        UsageReading reading = new UsageReading("openai", NOW,
                List.of(new UsageWindowReading(UsageWindowType.FIVE_HOUR, 40.0, future(1))),
                null, List.of(), "pro");
        ProviderViewModel model = model("openai", reading);

        assertEquals(ProviderViewModel.PresentationState.DATA, model.state());
        assertEquals(List.of("Uso 5h"), model.tables().stream()
                .map(ProviderViewModel.TableViewModel::title).toList());
        assertEquals(List.of("Uso 5h", "Reinicia en"), model.tables().get(0).headers());
    }

    @Test
    void geminiConstruyeTresTablasConLosGruposEnOrden() {
        ProviderViewModel model = geminiModel(
                geminiGroup("Gemini 4 Argon", weekly(30)),
                geminiGroup("Claude and GPT models", window(1.84, 1), weekly(0.94)),
                geminiGroup("Gemini Models", window(20, 1), weekly(10)));

        assertEquals(List.of("Modelo", "Uso 5h", "Uso semanal"),
                model.tables().stream().map(ProviderViewModel.TableViewModel::title).toList());
        List<String> expected = List.of("Gemini Models", "Claude and GPT models", "Gemini 4 Argon");
        assertEquals(expected, model.tables().get(0).rows().stream()
                .map(ProviderViewModel.RowViewModel::label).toList());
        assertEquals(expected.size(), model.tables().get(1).rows().size());
        assertEquals(expected.size(), model.tables().get(2).rows().size());
    }

    @Test
    void geminiVentanaAusenteMuestraNaYConservaLaSemanal() {
        ProviderViewModel model = geminiModel(
                geminiGroup("Gemini 4 Argon", weekly(30)),
                geminiGroup("Claude and GPT models", window(1.84, 1), weekly(0.94)));

        ProviderViewModel.RowViewModel argonFiveHours = model.tables().get(1).rows().get(1);
        assertEquals(ProviderViewModel.CellViewModel.CellType.NA,
                argonFiveHours.percentageCell().type());
        assertEquals("N/A", argonFiveHours.timeCell().text());
        ProviderViewModel.RowViewModel claudeWeekly = model.tables().get(2).rows().get(0);
        assertEquals(0.94, claudeWeekly.percentageCell().percentage());
    }

    @Test
    void geminiOmiteGruposSinCubosYAñadeLosDesconocidos() {
        ProviderViewModel model = geminiModel(
                geminiGroup("vacío"),
                geminiGroup("Otro grupo", window(12, 1)));

        assertEquals(List.of("Otro grupo"), model.tables().get(0).rows().stream()
                .map(ProviderViewModel.RowViewModel::label).toList());
        assertEquals(ProviderViewModel.CellViewModel.CellType.NA,
                model.tables().get(2).rows().get(0).percentageCell().type());
    }

    @Test
    void openCodeConstruyeTablasAlineadasConTotal() {
        ProviderViewModel model = model("opencode", openCodeReading(
                openCodeSeries("modelo-a", 50, 20, 10),
                openCodeSeries("modelo-b", 25, 10, 5),
                openCodeSeries("Total", 31.25, 30, 15)));

        assertEquals(java.util.Arrays.asList("Modelo", null), model.tables().stream()
                .map(ProviderViewModel.TableViewModel::title).toList());
        assertEquals(List.of("modelo-a", "modelo-b", "Total"),
                model.tables().get(0).rows().stream().map(ProviderViewModel.RowViewModel::label).toList());
        assertEquals(3, model.tables().get(1).headers().size());
        assertEquals(3, model.tables().get(0).rows().size());
        assertEquals(3, model.tables().get(1).rows().size());
        assertEquals(31.25, model.tables().get(1).rows().get(2).percentageCells().get(0).percentage());
        assertEquals(50.0, model.tables().get(1).rows().get(0).percentageCell().percentage());
        assertEquals(null, model.tables().get(0).rows().get(0).timeCell());
        assertEquals(null, model.tables().get(1).rows().get(0).label());
    }

    @Test
    void openCodeConsultaPosteriorAñadeModeloEnAmbasTablasSinCabecerasDuplicadas() {
        ProviderViewModel model = model("opencode", openCodeReading(
                openCodeSeries("modelo-a", 50, 20, 10),
                openCodeSeries("modelo-b", 10, 5, 2),
                openCodeSeries("Total", 20, 10, 5)));

        assertEquals(List.of("Modelo"), model.tables().get(0).headers());
        assertEquals(List.of("Uso 5h", "Uso semanal", "Uso mensual"), model.tables().get(1).headers());
        assertEquals(3, model.tables().get(0).rows().size());
        assertEquals(3, model.tables().get(1).rows().size());
    }

    private static UsageReading openCodeReading(UsageReading... series) {
        return new UsageReading("opencode", NOW, List.of(), List.of(series));
    }

    private static UsageReading openCodeSeries(String name, double fiveHour,
                                               double weekly, double monthly) {
        return new UsageReading("opencode", name, NOW, List.of(
                new UsageWindowReading(UsageWindowType.FIVE_HOUR, fiveHour, null),
                new UsageWindowReading(UsageWindowType.WEEKLY, weekly, null),
                new UsageWindowReading(UsageWindowType.MONTHLY, monthly, null)));
    }

    private static ProviderViewModel geminiModel(UsageReading... groups) {
        UsageReading reading = new UsageReading("gemini", NOW, List.of(), List.of(groups));
        return model("gemini", reading);
    }

    private static UsageReading geminiGroup(String name, UsageWindowReading... windows) {
        return new UsageReading("gemini", name, NOW, List.of(windows));
    }

    private static UsageWindowReading window(double percentage, long hours) {
        return new UsageWindowReading(UsageWindowType.FIVE_HOUR, percentage, future(hours));
    }

    private static UsageWindowReading weekly(double percentage) {
        return new UsageWindowReading(UsageWindowType.SEVEN_DAY, percentage, future(2));
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
