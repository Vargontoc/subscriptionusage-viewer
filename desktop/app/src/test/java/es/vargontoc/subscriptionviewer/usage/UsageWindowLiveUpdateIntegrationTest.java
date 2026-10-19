package es.vargontoc.subscriptionviewer.usage;

import java.awt.GraphicsEnvironment;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import es.vargontoc.subscriptionviewer.config.ConfigurationProvider;
import es.vargontoc.subscriptionviewer.ui.UsageCardPanel;
import es.vargontoc.subscriptionviewer.ui.UsageWindow;

/**
 * Pruebas de integración de la actualización en vivo de la ventana de uso.
 *
 * <p>Ensambla una fuente mutable, el poller, el historial, el publisher y la
 * ventana con reloj controlado, y verifica que las lecturas nuevas cruzan al
 * EDT y actualizan tarjetas y gráfico sin interacción del usuario.</p>
 */
@EnabledIf("notHeadless")
class UsageWindowLiveUpdateIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-10-16T10:00:00Z");
    private static final ZoneId ZONE = ZoneId.systemDefault();

    @BeforeEach
    void setUp() {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
    }


    static boolean notHeadless() {
        return !GraphicsEnvironment.isHeadless();
    }

    @Test
    void shouldUpdateWindowAfterPollerCycle() throws Exception {
        MutableUsageSource source = new MutableUsageSource("claude",
                readingOf("claude", 40, 15, T0.plusSeconds(3600), T0.plusSeconds(86400)));
        UsageHistory history = new UsageHistory();
        UsageSnapshotPublisher publisher = new UsageSnapshotPublisher();
        CountdownEvaluator evaluator = new CountdownEvaluator(Clock.fixed(T0, ZONE));
        UsageWindow window = new UsageWindow(publisher, evaluator, history);
        window.subscribe();

        SwingUtilities.invokeAndWait(() -> {
            window.getCardsPanel();
            window.getChartPanel();
        });

        UsagePoller poller = createPoller(source, history, publisher);
        poller.runCycle();
        drainEdt();

        assertThat(window.getCardsPanel().getComponentCount()).isEqualTo(1);
        UsageCardPanel card = (UsageCardPanel) window.getCardsPanel().getComponent(0);
        assertThat(labelTextsOf(card)).anyMatch(text -> text.contains("40%"));
        assertThat(labelTextsOf(card)).anyMatch(text -> text.contains("15%"));

        assertThat(history.getAllSeries()).isNotEmpty();
        assertThat(window.getChartPanel().isVisible()).isTrue();
    }

    @Test
    void shouldHideWindowWhenSourceReturnsEmpty() throws Exception {
        MutableUsageSource source = new MutableUsageSource("claude",
                readingOf("claude", 40, 15, T0.plusSeconds(3600), T0.plusSeconds(86400)));
        UsageHistory history = new UsageHistory();
        UsageSnapshotPublisher publisher = new UsageSnapshotPublisher();
        CountdownEvaluator evaluator = new CountdownEvaluator(Clock.fixed(T0, ZONE));
        UsageWindow window = new UsageWindow(publisher, evaluator, history);
        window.subscribe();

        SwingUtilities.invokeAndWait(() -> {
            window.getCardsPanel();
            window.getNeutralLabel();
        });

        UsagePoller poller = createPoller(source, history, publisher);
        poller.runCycle();
        drainEdt();

        assertThat(window.getCardsPanel().getComponentCount()).isEqualTo(1);

        source.setReading(Optional.empty());
        poller.runCycle();
        drainEdt();

        assertThat(window.getCardsPanel().getComponentCount()).isEqualTo(0);
        assertThat(window.getNeutralLabel().isVisible()).isTrue();
    }

    @Test
    void shouldRestoreWindowAfterEmptyThenValid() throws Exception {
        MutableUsageSource source = new MutableUsageSource("claude", Optional.empty());
        UsageHistory history = new UsageHistory();
        UsageSnapshotPublisher publisher = new UsageSnapshotPublisher();
        CountdownEvaluator evaluator = new CountdownEvaluator(Clock.fixed(T0, ZONE));
        UsageWindow window = new UsageWindow(publisher, evaluator, history);
        window.subscribe();

        SwingUtilities.invokeAndWait(() -> {
            window.getCardsPanel();
            window.getNeutralLabel();
        });

        UsagePoller poller = createPoller(source, history, publisher);
        poller.runCycle();
        drainEdt();

        assertThat(window.getCardsPanel().getComponentCount()).isEqualTo(0);
        assertThat(window.getNeutralLabel().isVisible()).isTrue();

        source.setReading(Optional.of(
                readingOf("claude", 40, 15, T0.plusSeconds(3600), T0.plusSeconds(86400))));
        poller.runCycle();
        drainEdt();

        assertThat(window.getNeutralLabel().isVisible()).isFalse();
        assertThat(window.getCardsPanel().getComponentCount()).isEqualTo(1);
    }

    private UsagePoller createPoller(MutableUsageSource source, UsageHistory history,
                                     UsageSnapshotPublisher publisher) {
        ConfigurationProvider config = mock(ConfigurationProvider.class);
        when(config.getUsagePollInterval()).thenReturn(Duration.ofSeconds(10));
        return new UsagePoller(config, List.of(source), history, publisher,
                Clock.fixed(T0, ZONE), Executors.newSingleThreadScheduledExecutor());
    }

    private void drainEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
        });
    }

    private List<String> labelTextsOf(UsageCardPanel card) {
        return java.util.Arrays.stream(card.getComponents())
                .filter(c -> c instanceof javax.swing.JLabel)
                .map(c -> ((javax.swing.JLabel) c).getText())
                .toList();
    }

    private UsageReading readingOf(String providerId, int fiveHour, int sevenDay,
                                   Instant fiveHourReset, Instant sevenDayReset) {
        return new UsageReading(
                providerId,
                T0,
                List.of(
                        new UsageWindowReading(UsageWindowType.FIVE_HOUR, fiveHour, fiveHourReset),
                        new UsageWindowReading(UsageWindowType.SEVEN_DAY, sevenDay, sevenDayReset)
                )
        );
    }

    private static final class MutableUsageSource implements UsageSource {

        private final String providerId;
        private final AtomicReference<Optional<UsageReading>> reading;

        MutableUsageSource(String providerId, UsageReading reading) {
            this.providerId = providerId;
            this.reading = new AtomicReference<>(Optional.ofNullable(reading));
        }

        MutableUsageSource(String providerId, Optional<UsageReading> reading) {
            this.providerId = providerId;
            this.reading = new AtomicReference<>(reading);
        }

        void setReading(Optional<UsageReading> reading) {
            this.reading.set(reading);
        }

        @Override
        public String getProviderId() {
            return providerId;
        }

        @Override
        public Optional<UsageReading> doRead() {
            return reading.get();
        }
    }
}
