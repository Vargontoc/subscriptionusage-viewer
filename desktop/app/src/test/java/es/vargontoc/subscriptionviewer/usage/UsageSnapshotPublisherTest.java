package es.vargontoc.subscriptionviewer.usage;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas unitarias de {@link UsageSnapshotPublisher}.
 */
class UsageSnapshotPublisherTest {

    private static final String CLAUDE = "claude";
    private static final String OPENAI = "openai";
    private static final String OTHER = "other";
    private static final Instant NOW = Instant.parse("2026-10-16T10:32:15Z");

    @Test
    void shouldNotifyClaudeStateFromDispatcherThread() {
        UsageSnapshotPublisher publisher = new UsageSnapshotPublisher(Runnable::run);
        AtomicReference<UsageSnapshot> captured = new AtomicReference<>();
        publisher.addListener(captured::set);

        UsageReading reading = readingOf(CLAUDE, 40, 15);
        publisher.publish(CLAUDE, Optional.of(reading));

        UsageSnapshot snapshot = captured.get();
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.readingOf(CLAUDE)).isPresent();
        assertThat(snapshot.readingOf(CLAUDE).get().windows()).hasSize(2);
    }

    @Test
    void shouldRemoveClaudeOnAbsenceWithoutAffectingOthers() {
        UsageSnapshotPublisher publisher = new UsageSnapshotPublisher(Runnable::run);
        List<UsageSnapshot> snapshots = new ArrayList<>();
        publisher.addListener(snapshots::add);

        publisher.publish(CLAUDE, Optional.of(readingOf(CLAUDE, 40, 15)));
        publisher.publish(OTHER, Optional.of(readingOf(OTHER, 10, 5)));
        publisher.publish(CLAUDE, Optional.empty());

        UsageSnapshot last = snapshots.get(snapshots.size() - 1);
        assertThat(last.readingOf(CLAUDE)).isEmpty();
        assertThat(last.readingOf(OTHER)).isPresent();
    }

    @Test
    void shouldReturnEmptySnapshotWhenNoReadings() {
        UsageSnapshotPublisher publisher = new UsageSnapshotPublisher(Runnable::run);

        UsageSnapshot snapshot = publisher.getCurrentSnapshot();

        assertThat(snapshot.isEmpty()).isTrue();
    }

    @Test
    void shouldReplacePreviousReadingForSameProvider() {
        UsageSnapshotPublisher publisher = new UsageSnapshotPublisher(Runnable::run);
        List<UsageSnapshot> snapshots = new ArrayList<>();
        publisher.addListener(snapshots::add);

        publisher.publish(CLAUDE, Optional.of(readingOf(CLAUDE, 40, 15)));
        publisher.publish(CLAUDE, Optional.of(readingOf(CLAUDE, 42, 16)));

        UsageSnapshot last = snapshots.get(snapshots.size() - 1);
        assertThat(last.readingOf(CLAUDE)).isPresent();
        assertThat(last.readingOf(CLAUDE).get().windows().get(0).usedPercentage()).isEqualTo(42);
        assertThat(last.readingOf(CLAUDE).get().windows().get(1).usedPercentage()).isEqualTo(16);
    }

    @Test
    void successfulReadPublishesCorrectStateAndQueryTime() {
        UsageSnapshotPublisher publisher = new UsageSnapshotPublisher(Runnable::run);
        publisher.publish(CLAUDE, UsageReadResult.datos(readingOf(CLAUDE, 20, 5)), NOW);

        UsageProviderState state = publisher.getCurrentSnapshot().stateOf(CLAUDE).orElseThrow();
        assertThat(state.estado()).isEqualTo(UsageProviderState.Estado.CORRECTO);
        assertThat(state.reading()).isNotNull();
        assertThat(state.lastSuccessfulQuery()).isEqualTo(NOW);
    }

    @Test
    void successfulOpenAiReadPreservesPlanInPublicSnapshot() {
        UsageSnapshotPublisher publisher = new UsageSnapshotPublisher(Runnable::run);
        UsageReading reading = new UsageReading(OPENAI, NOW, List.of(
                new UsageWindowReading(UsageWindowType.FIVE_HOUR, 20, NOW.plusSeconds(3600))),
                null, List.of(), "plus");

        publisher.publish(OPENAI, UsageReadResult.datos(reading), NOW);

        assertThat(publisher.getCurrentSnapshot().readingOf(OPENAI).orElseThrow().planType())
                .isEqualTo("plus");
    }

    @Test
    void transientFailureKeepsDataAndSuccessfulTimeButMarksItStale() {
        UsageSnapshotPublisher publisher = new UsageSnapshotPublisher(Runnable::run);
        publisher.publish(CLAUDE, UsageReadResult.datos(readingOf(CLAUDE, 20, 5)), NOW);
        publisher.publish(CLAUDE, UsageReadResult.falloTransitorio(new RuntimeException("network")),
                NOW.plusSeconds(60));

        UsageProviderState state = publisher.getCurrentSnapshot().stateOf(CLAUDE).orElseThrow();
        assertThat(state.estado()).isEqualTo(UsageProviderState.Estado.DESACTUALIZADO);
        assertThat(state.reading().windows().get(0).usedPercentage()).isEqualTo(20);
        assertThat(state.lastSuccessfulQuery()).isEqualTo(NOW);

        publisher.publish(CLAUDE, UsageReadResult.datos(readingOf(CLAUDE, 21, 5)), NOW.plusSeconds(120));
        assertThat(publisher.getCurrentSnapshot().stateOf(CLAUDE).orElseThrow().estado())
                .isEqualTo(UsageProviderState.Estado.CORRECTO);
    }

    @Test
    void openAiSessionExpiryKeepsPreviousDataAndSuccessfulTime() {
        UsageSnapshotPublisher publisher = new UsageSnapshotPublisher(Runnable::run);
        publisher.publish(OPENAI, UsageReadResult.datos(readingOf(OPENAI, 20, 5)), NOW);

        publisher.publish(OPENAI, UsageReadResult.sesionCaducada(), NOW.plusSeconds(60));

        UsageProviderState state = publisher.getCurrentSnapshot().stateOf(OPENAI).orElseThrow();
        assertThat(state.estado()).isEqualTo(UsageProviderState.Estado.SESION_CADUCADA);
        assertThat(state.reading().windows().get(0).usedPercentage()).isEqualTo(20);
        assertThat(state.lastSuccessfulQuery()).isEqualTo(NOW);
    }

    @Test
    void openAiSessionExpiryIsVisibleWithoutPreviousData() {
        UsageSnapshotPublisher publisher = new UsageSnapshotPublisher(Runnable::run);

        publisher.publish(OPENAI, UsageReadResult.sesionCaducada(), NOW);

        UsageProviderState state = publisher.getCurrentSnapshot().stateOf(OPENAI).orElseThrow();
        assertThat(state.estado()).isEqualTo(UsageProviderState.Estado.SESION_CADUCADA);
        assertThat(state.reading()).isNull();
        assertThat(state.lastSuccessfulQuery()).isNull();
    }

    @Test
    void openAiInitialTransientFailureDoesNotPublishSectionOrQueryTime() {
        UsageSnapshotPublisher publisher = new UsageSnapshotPublisher(Runnable::run);

        publisher.publish(OPENAI, UsageReadResult.falloTransitorio(null), NOW);

        assertThat(publisher.getCurrentSnapshot().stateOf(OPENAI)).isEmpty();
        assertThat(publisher.getCurrentSnapshot().readingOf(OPENAI)).isEmpty();
    }

    @Test
    void openCodeFailureAfterSuccessKeepsDataAndSuccessfulTimeWithoutStaleState() {
        UsageSnapshotPublisher publisher = new UsageSnapshotPublisher(Runnable::run);
        publisher.publish("opencode", UsageReadResult.datos(readingOf("opencode", 20, 5)), NOW);
        publisher.publish("opencode", UsageReadResult.falloTransitorio(new RuntimeException("cli")),
                NOW.plusSeconds(60));

        UsageProviderState state = publisher.getCurrentSnapshot().stateOf("opencode").orElseThrow();
        assertThat(state.estado()).isEqualTo(UsageProviderState.Estado.CORRECTO);
        assertThat(state.reading().windows()).hasSize(2);
        assertThat(state.lastSuccessfulQuery()).isEqualTo(NOW);
    }

    @Test
    void initialOpenCodeAbsenceHasNoPublishedState() {
        UsageSnapshotPublisher publisher = new UsageSnapshotPublisher(Runnable::run);
        publisher.publish("opencode", Optional.empty());

        assertThat(publisher.getCurrentSnapshot().stateOf("opencode")).isEmpty();
    }

    @Test
    void initialOpenCodeTransientFailureHasNoPublishedState() {
        UsageSnapshotPublisher publisher = new UsageSnapshotPublisher(Runnable::run);
        publisher.publish("opencode", UsageReadResult.falloTransitorio(new RuntimeException("x")), NOW);

        assertThat(publisher.getCurrentSnapshot().stateOf("opencode")).isEmpty();
    }

    @Test
    void providersAreIsolated() {
        UsageSnapshotPublisher publisher = new UsageSnapshotPublisher(Runnable::run);
        publisher.publish(CLAUDE, UsageReadResult.datos(readingOf(CLAUDE, 20, 5)), NOW);
        publisher.publish("opencode", UsageReadResult.datos(readingOf("opencode", 30, 7)), NOW);
        publisher.publish("opencode", UsageReadResult.falloTransitorio(new RuntimeException("x")), NOW);

        assertThat(publisher.getCurrentSnapshot().stateOf(CLAUDE).orElseThrow().estado())
                .isEqualTo(UsageProviderState.Estado.CORRECTO);
        assertThat(publisher.getCurrentSnapshot().stateOf(CLAUDE).orElseThrow().reading().providerId())
                .isEqualTo(CLAUDE);
    }

    @Test
    void openCodeAbsenceDoesNotRemoveClaudeCountdownState() {
        UsageSnapshotPublisher publisher = new UsageSnapshotPublisher(Runnable::run);
        publisher.publish(CLAUDE, UsageReadResult.datos(readingOf(CLAUDE, 40, 15)), NOW);
        publisher.publish("opencode", Optional.empty());

        UsageSnapshot snapshot = publisher.getCurrentSnapshot();
        assertThat(snapshot.stateOf(CLAUDE)).isPresent();
        assertThat(snapshot.stateOf(CLAUDE).orElseThrow().estado())
                .isEqualTo(UsageProviderState.Estado.CORRECTO);
        assertThat(snapshot.stateOf("opencode")).isEmpty();
        assertThat(snapshot.readingOf(CLAUDE)).isPresent();
    }

    private UsageReading readingOf(String providerId, int fiveHour, int sevenDay) {
        return new UsageReading(
                providerId,
                NOW,
                List.of(
                        new UsageWindowReading(UsageWindowType.FIVE_HOUR, fiveHour, NOW.plusSeconds(3600)),
                        new UsageWindowReading(UsageWindowType.SEVEN_DAY, sevenDay, NOW.plusSeconds(86400))
                )
        );
    }
}
