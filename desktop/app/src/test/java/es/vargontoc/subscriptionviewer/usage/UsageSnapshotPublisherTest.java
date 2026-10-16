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
    void shouldRemoveProviderOnAbsenceWithoutAffectingOthers() {
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
