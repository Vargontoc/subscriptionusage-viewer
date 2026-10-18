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

    private UsageReading row(String seriesId, int rolling, int weekly, int monthly) {
        return new UsageReading("opencode", seriesId, WRITTEN_AT, List.of(
                new UsageWindowReading(UsageWindowType.ROLLING, rolling, WRITTEN_AT.plusSeconds(600)),
                new UsageWindowReading(UsageWindowType.WEEKLY, weekly, WRITTEN_AT.plusSeconds(1_200)),
                new UsageWindowReading(UsageWindowType.MONTHLY, monthly, WRITTEN_AT.plusSeconds(1_800))));
    }

    private List<String> rowTexts(UsageCardPanel panel) {
        return java.util.Arrays.stream(panel.getComponents())
                .filter(component -> component instanceof JLabel label
                        && Boolean.TRUE.equals(label.getClientProperty("opencode-row")))
                .map(component -> ((JLabel) component).getText())
                .toList();
    }
}
