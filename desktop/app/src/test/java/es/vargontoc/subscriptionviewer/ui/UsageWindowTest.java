package es.vargontoc.subscriptionviewer.ui;

import es.vargontoc.subscriptionviewer.usage.CountdownEvaluator;
import es.vargontoc.subscriptionviewer.usage.UsageHistory;
import es.vargontoc.subscriptionviewer.usage.UsageReading;
import es.vargontoc.subscriptionviewer.usage.UsageSnapshot;
import es.vargontoc.subscriptionviewer.usage.UsageSnapshotPublisher;
import es.vargontoc.subscriptionviewer.usage.UsageWindowReading;
import es.vargontoc.subscriptionviewer.usage.UsageWindowType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Pruebas unitarias de {@link UsageWindow}.
 *
 * <p>Se ejecutan solo cuando el entorno no es headless, ya que requieren
 * componentes Swing.</p>
 */
class UsageWindowTest {

    private static final Instant WRITTEN_AT = Instant.parse("2026-10-16T10:32:15Z");
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private MutableClock clock;
    private CountdownEvaluator evaluator;

    @BeforeEach
    void setUp() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Entorno headless: se omiten tests de UI");
        clock = new MutableClock(WRITTEN_AT, ZONE);
        evaluator = new CountdownEvaluator(clock);
    }

    @Test
    void shouldShowFortyAndFifteenPercentWithLocalTime() throws Exception {
        UsageSnapshotPublisher publisher = new UsageSnapshotPublisher();
        UsageHistory history = new UsageHistory();
        UsageWindow window = new UsageWindow(publisher, evaluator, history);
        window.subscribe();

        UsageSnapshot snapshot = new UsageSnapshot(java.util.Map.of("claude",
                readingOf("claude", 40, 15, WRITTEN_AT.plusSeconds(3600), WRITTEN_AT.plusSeconds(86400))));
        window.onSnapshotChanged(snapshot);
        drainEdt();

        assertThat(window.getNeutralLabel().isVisible()).isFalse();
        assertThat(window.getCardsPanel().getComponentCount()).isEqualTo(1);

        UsageCardPanel card = (UsageCardPanel) window.getCardsPanel().getComponent(0);
        assertThat(labelTextsOf(card)).anyMatch(text -> text.contains("40%"));
        assertThat(labelTextsOf(card)).anyMatch(text -> text.contains("15%"));

        LocalDateTime localTime = LocalDateTime.ofInstant(WRITTEN_AT, ZONE);
        String expectedTime = localTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        assertThat(labelTextsOf(card)).anyMatch(text -> text.contains(expectedTime));
    }

    @Test
    void shouldShowOpenAiAsSeparateSectionWithBothWindows() throws Exception {
        UsageSnapshotPublisher publisher = new UsageSnapshotPublisher();
        UsageHistory history = new UsageHistory();
        UsageWindow window = new UsageWindow(publisher, evaluator, history);
        window.subscribe();

        UsageSnapshot snapshot = new UsageSnapshot(java.util.Map.of(
                "claude", readingOf("claude", 40, 15, WRITTEN_AT.plusSeconds(3600), WRITTEN_AT.plusSeconds(86400)),
                "openai", readingOf("openai", 6, 1, WRITTEN_AT.plusSeconds(3600), WRITTEN_AT.plusSeconds(86400))));
        window.onSnapshotChanged(snapshot);
        drainEdt();

        assertThat(window.getCardsPanel().getComponentCount()).isEqualTo(2);
        UsageCardPanel openAi = java.util.Arrays.stream(window.getCardsPanel().getComponents())
                .map(component -> (UsageCardPanel) component)
                .filter(card -> labelTextsOf(card).stream().anyMatch(text -> text.contains("OpenAI")))
                .findFirst().orElseThrow();
        assertThat(labelTextsOf(openAi)).anyMatch(text -> text.contains("6%"));
        assertThat(labelTextsOf(openAi)).anyMatch(text -> text.contains("1%"));
        assertThat(labelTextsOf(openAi)).anyMatch(text -> text.contains("Última actualización"));
    }

    @Test
    void shouldShowNeutralTextWhenSnapshotIsEmpty() throws Exception {
        UsageSnapshotPublisher publisher = new UsageSnapshotPublisher();
        UsageHistory history = new UsageHistory();
        UsageWindow window = new UsageWindow(publisher, evaluator, history);
        window.subscribe();

        window.onSnapshotChanged(new UsageSnapshot());
        drainEdt();

        assertThat(window.getNeutralLabel().isVisible()).isTrue();
        assertThat(window.getCardsPanel().getComponentCount()).isEqualTo(0);
        assertThat(window.getNeutralLabel().getText())
                .doesNotContain("error", "fallo", "advertencia", "aviso")
                .contains("No hay datos");
    }

    @Test
    void shouldNotCreateSectionsForOtherProviders() throws Exception {
        UsageSnapshotPublisher publisher = new UsageSnapshotPublisher();
        UsageHistory history = new UsageHistory();
        UsageWindow window = new UsageWindow(publisher, evaluator, history);
        window.subscribe();

        UsageSnapshot snapshot = new UsageSnapshot(java.util.Map.of("claude",
                readingOf("claude", 40, 15, WRITTEN_AT.plusSeconds(3600), WRITTEN_AT.plusSeconds(86400))));
        window.onSnapshotChanged(snapshot);
        drainEdt();

        assertThat(window.getCardsPanel().getComponentCount()).isEqualTo(1);
        UsageCardPanel card = (UsageCardPanel) window.getCardsPanel().getComponent(0);
        assertThat(labelTextsOf(card)).noneMatch(text -> text.toLowerCase().contains("openai") || text.toLowerCase().contains("otro"));
    }

    @Test
    void shouldShowFutureResetTime() throws Exception {
        UsageSnapshotPublisher publisher = new UsageSnapshotPublisher();
        UsageHistory history = new UsageHistory();
        UsageWindow window = new UsageWindow(publisher, evaluator, history);
        window.subscribe();

        Instant twoHoursTenMinutes = WRITTEN_AT.plus(Duration.ofHours(2).plusMinutes(10));
        UsageSnapshot snapshot = new UsageSnapshot(java.util.Map.of("claude",
                readingOf("claude", 40, 15, twoHoursTenMinutes, WRITTEN_AT.plusSeconds(86400))));
        window.onSnapshotChanged(snapshot);
        drainEdt();

        UsageCardPanel card = (UsageCardPanel) window.getCardsPanel().getComponent(0);
        assertThat(labelTextsOf(card)).anyMatch(text -> text.contains("2h 10m"));
    }

    @Test
    void shouldHideExpiredWindowAndShowNeutralText() throws Exception {
        UsageSnapshotPublisher publisher = new UsageSnapshotPublisher();
        UsageHistory history = new UsageHistory();
        UsageWindow window = new UsageWindow(publisher, evaluator, history);
        window.subscribe();

        Instant past = WRITTEN_AT.minusSeconds(60);
        UsageSnapshot snapshot = new UsageSnapshot(java.util.Map.of("claude",
                readingOf("claude", 40, 15, past, past)));
        window.onSnapshotChanged(snapshot);
        drainEdt();

        assertThat(window.getCardsPanel().getComponentCount()).isEqualTo(0);
        assertThat(window.getNeutralLabel().isVisible()).isTrue();
    }

    @Test
    void shouldRestoreCardAfterAllExpiredWhenNewValidSnapshotArrives() throws Exception {
        UsageSnapshotPublisher publisher = new UsageSnapshotPublisher();
        UsageHistory history = new UsageHistory();
        UsageWindow window = new UsageWindow(publisher, evaluator, history);
        window.subscribe();

        Instant past = WRITTEN_AT.minusSeconds(60);
        UsageSnapshot expired = new UsageSnapshot(java.util.Map.of("claude",
                readingOf("claude", 40, 15, past, past)));
        window.onSnapshotChanged(expired);
        drainEdt();

        assertThat(window.getCardsPanel().getComponentCount()).isEqualTo(0);
        assertThat(window.getNeutralLabel().isVisible()).isTrue();

        UsageSnapshot valid = new UsageSnapshot(java.util.Map.of("claude",
                readingOf("claude", 40, 15, WRITTEN_AT.plusSeconds(3600), WRITTEN_AT.plusSeconds(86400))));
        window.onSnapshotChanged(valid);
        drainEdt();

        assertThat(window.getNeutralLabel().isVisible()).isFalse();
        assertThat(window.getCardsPanel().getComponentCount()).isEqualTo(1);
    }

    @Test
    void shouldRefreshOpenCodeChartFromInMemoryHistoryOnSnapshot() throws Exception {
        UsageSnapshotPublisher publisher = new UsageSnapshotPublisher();
        UsageHistory history = new UsageHistory();
        UsageWindow window = new UsageWindow(publisher, evaluator, history);
        window.subscribe();

        for (int i = 0; i < 3; i++) {
            Instant at = WRITTEN_AT.plusSeconds(i * 60L);
            history.add(new UsageReading("opencode", at, List.of(
                    new UsageWindowReading(UsageWindowType.ROLLING, 10 + i, at.plusSeconds(600)),
                    new UsageWindowReading(UsageWindowType.WEEKLY, 20 + i, at.plusSeconds(1_200)))));
        }
        window.onSnapshotChanged(new UsageSnapshot(java.util.Map.of("opencode",
                new UsageReading("opencode", WRITTEN_AT.plusSeconds(120), List.of(
                        new UsageWindowReading(UsageWindowType.ROLLING, 12, WRITTEN_AT.plusSeconds(600)),
                        new UsageWindowReading(UsageWindowType.WEEKLY, 22, WRITTEN_AT.plusSeconds(1_200)))))));
        drainEdt();

        assertThat(window.getChartPanel()
                .projectSeries("opencode", UsageWindowType.ROLLING)).hasSize(3);
        assertThat(window.getChartPanel()
                .projectSeries("opencode", UsageWindowType.WEEKLY)).hasSize(3);
    }

    @Test
    void shouldRefreshOpenAiChartFromInMemoryHistoryOnEachSnapshot() throws Exception {
        UsageSnapshotPublisher publisher = new UsageSnapshotPublisher();
        UsageHistory history = new UsageHistory();
        UsageWindow window = new UsageWindow(publisher, evaluator, history);
        window.subscribe();

        for (int i = 0; i < 3; i++) {
            Instant at = WRITTEN_AT.plusSeconds(i * 60L);
            history.add(new UsageReading("openai", at, List.of(
                    new UsageWindowReading(UsageWindowType.FIVE_HOUR, 10 + i, at.plusSeconds(600)),
                    new UsageWindowReading(UsageWindowType.SEVEN_DAY, 20 + i, at.plusSeconds(1_200)))));
            window.onSnapshotChanged(new UsageSnapshot(java.util.Map.of(
                    "openai", readingOf("openai", 10 + i, 20 + i,
                            at.plusSeconds(600), at.plusSeconds(1_200)))));
            drainEdt();
        }

        assertThat(window.getChartPanel().projectOpenAiSeries(UsageWindowType.FIVE_HOUR))
                .hasSize(3);
        assertThat(window.getChartPanel().projectOpenAiSeries(UsageWindowType.SEVEN_DAY))
                .hasSize(3);
    }

    private void drainEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
        });
    }

    private List<String> labelTextsOf(UsageCardPanel card) {
        return java.util.Arrays.stream(card.getComponents())
                .filter(c -> c instanceof JLabel)
                .map(c -> ((JLabel) c).getText())
                .toList();
    }

    private UsageReading readingOf(String providerId, int fiveHour, int sevenDay,
                                   Instant fiveHourReset, Instant sevenDayReset) {
        return new UsageReading(
                providerId,
                WRITTEN_AT,
                List.of(
                        new UsageWindowReading(UsageWindowType.FIVE_HOUR, fiveHour, fiveHourReset),
                        new UsageWindowReading(UsageWindowType.SEVEN_DAY, sevenDay, sevenDayReset)
                )
        );
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
