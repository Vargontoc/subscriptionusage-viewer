package es.vargontoc.subscriptionviewer.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.Clock;
import java.util.List;

import javax.swing.JLabel;

import org.junit.jupiter.api.Test;

import es.vargontoc.subscriptionviewer.usage.CountdownEvaluator;
import es.vargontoc.subscriptionviewer.usage.UsageProviderState;
import es.vargontoc.subscriptionviewer.usage.UsageReading;
import es.vargontoc.subscriptionviewer.usage.UsageWindowReading;
import es.vargontoc.subscriptionviewer.usage.UsageWindowType;

class UsageCardPanelTest {

    private static final Instant WRITTEN_AT = Instant.parse("2026-10-16T10:32:15Z");

    @Test
    void shouldHideOpenCodeModelsWithNoUsage() {
        UsageCardPanel panel = new UsageCardPanel("opencode", new CountdownEvaluator(Clock.systemUTC()));
        UsageReading modelWithUsage = row("model-used", 25, 0, 10);
        UsageReading modelWithoutUsage = row("model-unused", 0, 0, 0);
        UsageReading total = row("Total", 25, 0, 10);
        UsageReading reading = new UsageReading("opencode", WRITTEN_AT, List.of(),
                List.of(modelWithUsage, modelWithoutUsage, total));

        panel.update(new UsageProviderState(UsageProviderState.Estado.CORRECTO, reading, WRITTEN_AT));

        assertThat(rowTexts(panel)).containsExactlyInAnyOrder(
                "model-used  Rolling: 25% | Semanal: 0% | Mensual: 10%",
                "Total  Rolling: 25% | Semanal: 0% | Mensual: 10%");
    }

    @Test
    void shouldRenderOpenAiWindowsAndOptionalPlan() {
        UsageCardPanel panel = new UsageCardPanel("openai", new CountdownEvaluator(Clock.systemUTC()));
        Instant reset = Instant.now().plusSeconds(3_600);
        UsageReading reading = new UsageReading("openai", WRITTEN_AT, List.of(
                new UsageWindowReading(UsageWindowType.FIVE_HOUR, 6, reset),
                new UsageWindowReading(UsageWindowType.SEVEN_DAY, 1, reset)));

        panel.update(new UsageProviderState(UsageProviderState.Estado.CORRECTO, reading, WRITTEN_AT), "plus");

        assertThat(allTexts(panel)).anyMatch(text -> text.contains("OpenAI"));
        assertThat(allTexts(panel)).anyMatch(text -> text.contains("6%"));
        assertThat(allTexts(panel)).anyMatch(text -> text.contains("1%"));
        assertThat(allTexts(panel)).anyMatch(text -> text.contains("Plan: Plus"));
    }

    @Test
    void shouldRenderOpenAiWithoutPlanWhenPlanIsAbsent() {
        UsageCardPanel panel = new UsageCardPanel("openai", new CountdownEvaluator(Clock.systemUTC()));
        Instant reset = Instant.now().plusSeconds(3_600);
        UsageReading reading = new UsageReading("openai", WRITTEN_AT, List.of(
                new UsageWindowReading(UsageWindowType.FIVE_HOUR, 6, reset),
                new UsageWindowReading(UsageWindowType.SEVEN_DAY, 1, reset)));

        panel.update(new UsageProviderState(UsageProviderState.Estado.CORRECTO, reading, WRITTEN_AT));

        assertThat(allTexts(panel)).noneMatch(text -> text.contains("Plan:"));
    }

    @Test
    void shouldFormatDecimalPercentageOnlyWhenNeeded() {
        UsageCardPanel panel = new UsageCardPanel("openai", new CountdownEvaluator(Clock.systemUTC()));
        Instant reset = Instant.now().plusSeconds(3_600);
        UsageReading reading = new UsageReading("openai", WRITTEN_AT, List.of(
                new UsageWindowReading(UsageWindowType.FIVE_HOUR, 1.8, reset),
                new UsageWindowReading(UsageWindowType.SEVEN_DAY, 100, reset)));

        panel.update(new UsageProviderState(UsageProviderState.Estado.CORRECTO, reading, WRITTEN_AT));

        assertThat(allTexts(panel)).anyMatch(text -> text.contains("1.8%"));
        assertThat(allTexts(panel)).anyMatch(text -> text.contains("100%"));
        assertThat(allTexts(panel)).noneMatch(text -> text.contains("100.0%"));
    }

    @Test
    void shouldRenderGeminiGroupsWithOnlyTheirPresentWindows() {
        Instant lastSuccess = Instant.parse("2026-10-16T12:34:56Z");
        Instant reset = Instant.parse("2026-10-17T10:32:15Z");
        UsageReading reading = new UsageReading("gemini", WRITTEN_AT, List.of(), List.of(
                geminiGroup("Gemini Models", new UsageWindowReading(UsageWindowType.WEEKLY, 1.8, reset),
                        new UsageWindowReading(UsageWindowType.FIVE_HOUR, 0.9, reset)),
                geminiGroup("Claude and GPT models", new UsageWindowReading(UsageWindowType.WEEKLY, 0.9, reset),
                        new UsageWindowReading(UsageWindowType.FIVE_HOUR, 1.8, reset)),
                geminiGroup("Gemini 4 Argon", new UsageWindowReading(UsageWindowType.WEEKLY, 50, reset))));
        UsageCardPanel panel = new UsageCardPanel("gemini", new CountdownEvaluator(
                Clock.fixed(Instant.parse("2026-10-16T10:32:15Z"), java.time.ZoneOffset.UTC)));

        panel.update(new UsageProviderState(UsageProviderState.Estado.CORRECTO, reading, lastSuccess));

        assertThat(geminiRows(panel)).hasSize(3);
        assertThat(geminiRows(panel)).anyMatch(text -> text.startsWith("Gemini 4 Argon:")
                && text.contains("Semanal: 50%") && !text.contains("5 horas"));
        String expectedTime = java.time.LocalDateTime.ofInstant(lastSuccess, java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        assertThat(allTexts(panel)).anyMatch(text -> text.contains(expectedTime));
    }

    @Test
    void shouldRenderGeminiWindowWithoutResetWithoutCountdown() {
        UsageReading reading = new UsageReading("gemini", WRITTEN_AT, List.of(), List.of(
                geminiGroup("New group", new UsageWindowReading(UsageWindowType.WEEKLY, 1.8, null))));
        UsageCardPanel panel = new UsageCardPanel("gemini", new CountdownEvaluator(Clock.systemUTC()));

        panel.update(new UsageProviderState(UsageProviderState.Estado.CORRECTO, reading, WRITTEN_AT));

        assertThat(geminiRows(panel)).containsExactly("New group: Semanal: 1.8%");
    }

    @Test
    void shouldNotShowGeminiFailureMessageAfterSuccess() {
        UsageCardPanel panel = new UsageCardPanel("gemini", new CountdownEvaluator(Clock.systemUTC()));
        UsageReading reading = new UsageReading("gemini", WRITTEN_AT, List.of(), List.of(
                geminiGroup("Gemini Models", new UsageWindowReading(UsageWindowType.WEEKLY, 20, null))));

        panel.update(new UsageProviderState(UsageProviderState.Estado.CORRECTO, reading, WRITTEN_AT));

        assertThat(allTexts(panel)).doesNotContain(
                "No se ha podido obtener el consumo de Gemini. Por favor, compruebe que la herramienta agy está instalada y que ha iniciado sesión, y vuelva a intentarlo más tarde.");
        assertThat(panel.hasMessage()).isFalse();
    }

    @Test
    void shouldShowFormalGeminiFailureMessageWithoutCommandOutput() {
        UsageCardPanel panel = new UsageCardPanel("gemini", new CountdownEvaluator(Clock.systemUTC()));

        panel.update(new UsageProviderState(UsageProviderState.Estado.CONSULTA_FALLIDA, null, null));

        assertThat(allTexts(panel)).contains(
                "No se ha podido obtener el consumo de Gemini. Por favor, compruebe que la herramienta agy está instalada y que ha iniciado sesión, y vuelva a intentarlo más tarde.");
        assertThat(allTexts(panel)).noneMatch(text -> text.contains("COMMAND_OUTPUT_SENTINEL"));
        assertThat(geminiRows(panel)).isEmpty();
    }

    @Test
    void shouldKeepGeminiDataAndLastSuccessfulTimeOnFailureAndClearMessageOnRecovery() {
        UsageCardPanel panel = new UsageCardPanel("gemini", new CountdownEvaluator(Clock.systemUTC()));
        UsageReading reading = new UsageReading("gemini", WRITTEN_AT, List.of(), List.of(
                geminiGroup("Gemini Models", new UsageWindowReading(UsageWindowType.WEEKLY, 20, null))));
        String expectedTime = java.time.LocalDateTime.ofInstant(WRITTEN_AT, java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));

        panel.update(new UsageProviderState(UsageProviderState.Estado.CORRECTO, reading, WRITTEN_AT));
        panel.update(new UsageProviderState(UsageProviderState.Estado.CONSULTA_FALLIDA, reading, WRITTEN_AT));

        assertThat(geminiRows(panel)).containsExactly("Gemini Models: Semanal: 20%");
        assertThat(allTexts(panel)).anyMatch(text -> text.contains(expectedTime));
        assertThat(allTexts(panel)).contains(
                "No se ha podido obtener el consumo de Gemini. Por favor, compruebe que la herramienta agy está instalada y que ha iniciado sesión, y vuelva a intentarlo más tarde.");

        panel.update(new UsageProviderState(UsageProviderState.Estado.CORRECTO, reading, WRITTEN_AT));

        assertThat(allTexts(panel)).doesNotContain(
                "No se ha podido obtener el consumo de Gemini. Por favor, compruebe que la herramienta agy está instalada y que ha iniciado sesión, y vuelva a intentarlo más tarde.");
    }

    @Test
    void shouldRenderFormalOpenAiExpiredSessionMessageWithoutData() {
        UsageProviderCardPanel panel = new UsageProviderCardPanel("openai",
                new CountdownEvaluator(Clock.systemUTC()));

        panel.update(new UsageProviderState(UsageProviderState.Estado.SESION_CADUCADA, null, null));

        assertThat(allTexts(panel)).contains(UsageProviderCardPanel.OPENAI_EXPIRED_SESSION_MESSAGE);
        assertThat(allTexts(panel)).noneMatch(text -> text.toLowerCase().contains("token"));
        assertThat(panel.hasMessage()).isTrue();
        assertThat(panel.hasVisibleWindows()).isFalse();
    }

    @Test
    void shouldKeepLastDataAndMarkItStaleAfterTransientFailure() {
        UsageProviderCardPanel panel = new UsageProviderCardPanel("openai",
                new CountdownEvaluator(Clock.systemUTC()));
        UsageReading reading = new UsageReading("openai", WRITTEN_AT, List.of(
                new UsageWindowReading(UsageWindowType.FIVE_HOUR, 20, Instant.now().plusSeconds(3600))));

        panel.update(new UsageProviderState(UsageProviderState.Estado.DESACTUALIZADO, reading, WRITTEN_AT));

        assertThat(allTexts(panel)).anyMatch(text -> text.contains("20%"));
        assertThat(allTexts(panel)).contains("Datos desactualizados");
    }

    private UsageReading row(String seriesId, int rolling, int weekly, int monthly) {
        return new UsageReading("opencode", seriesId, WRITTEN_AT, List.of(
                new UsageWindowReading(UsageWindowType.ROLLING, rolling, WRITTEN_AT.plusSeconds(600)),
                new UsageWindowReading(UsageWindowType.WEEKLY, weekly, WRITTEN_AT.plusSeconds(1_200)),
                new UsageWindowReading(UsageWindowType.MONTHLY, monthly, WRITTEN_AT.plusSeconds(1_800))));
    }

    private UsageReading geminiGroup(String name, UsageWindowReading... windows) {
        return new UsageReading("gemini", name, WRITTEN_AT, List.of(windows));
    }

    private List<String> rowTexts(UsageCardPanel panel) {
        return java.util.Arrays.stream(panel.getComponents())
                .filter(component -> component instanceof JLabel label
                        && Boolean.TRUE.equals(label.getClientProperty("opencode-row")))
                .map(component -> ((JLabel) component).getText())
                .toList();
    }

    private List<String> geminiRows(UsageCardPanel panel) {
        return java.util.Arrays.stream(panel.getComponents())
                .filter(component -> component instanceof JLabel label
                        && Boolean.TRUE.equals(label.getClientProperty("gemini-row")))
                .map(component -> ((JLabel) component).getText())
                .toList();
    }

    private List<String> allTexts(UsageCardPanel panel) {
        return java.util.Arrays.stream(panel.getComponents())
                .filter(component -> component instanceof JLabel)
                .map(component -> ((JLabel) component).getText())
                .toList();
    }
}
