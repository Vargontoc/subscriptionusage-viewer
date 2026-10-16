package es.vargontoc.subscriptionviewer.usage;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas unitarias de {@link UsageHistory}.
 */
class UsageHistoryTest {

    private static final String PROVIDER = "claude";
    private static final Instant T1 = Instant.parse("2026-10-16T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-16T10:00:10Z");
    private static final Instant T3 = Instant.parse("2026-10-16T10:00:20Z");

    @Test
    void shouldCreateOrderedSeriesFromThreeIncreasingReadings() {
        UsageHistory history = new UsageHistory();

        history.add(PROVIDER, UsageWindowType.FIVE_HOUR, 10, T1);
        history.add(PROVIDER, UsageWindowType.FIVE_HOUR, 20, T2);
        history.add(PROVIDER, UsageWindowType.FIVE_HOUR, 30, T3);

        List<UsageHistoryPoint> series = history.getSeries(PROVIDER, UsageWindowType.FIVE_HOUR);
        assertThat(series).hasSize(3);
        assertThat(series.get(0).usedPercentage()).isEqualTo(10);
        assertThat(series.get(0).writtenAt()).isEqualTo(T1);
        assertThat(series.get(1).usedPercentage()).isEqualTo(20);
        assertThat(series.get(1).writtenAt()).isEqualTo(T2);
        assertThat(series.get(2).usedPercentage()).isEqualTo(30);
        assertThat(series.get(2).writtenAt()).isEqualTo(T3);
    }

    @Test
    void shouldNotDuplicateWhenWrittenAtIsEqualOrEarlier() {
        UsageHistory history = new UsageHistory();

        history.add(PROVIDER, UsageWindowType.FIVE_HOUR, 10, T1);
        history.add(PROVIDER, UsageWindowType.FIVE_HOUR, 20, T1);
        history.add(PROVIDER, UsageWindowType.FIVE_HOUR, 30, T1.minusSeconds(1));

        List<UsageHistoryPoint> series = history.getSeries(PROVIDER, UsageWindowType.FIVE_HOUR);
        assertThat(series).hasSize(1);
        assertThat(series.get(0).usedPercentage()).isEqualTo(10);
    }

    @Test
    void shouldKeepMostRecentPointsWhenLimitIsExceeded() {
        UsageHistory history = new UsageHistory();

        for (int i = 0; i < UsageHistory.MAX_POINTS_PER_SERIES + 5; i++) {
            history.add(PROVIDER, UsageWindowType.SEVEN_DAY, i, T1.plusSeconds(i));
        }

        List<UsageHistoryPoint> series = history.getSeries(PROVIDER, UsageWindowType.SEVEN_DAY);
        assertThat(series).hasSize(UsageHistory.MAX_POINTS_PER_SERIES);
        assertThat(series.get(0).usedPercentage()).isEqualTo(5);
        assertThat(series.get(series.size() - 1).usedPercentage()).isEqualTo(UsageHistory.MAX_POINTS_PER_SERIES + 4);
    }

    @Test
    void shouldBeEmptyWhenCreated() {
        UsageHistory history = new UsageHistory();

        assertThat(history.getSeries(PROVIDER, UsageWindowType.FIVE_HOUR)).isEmpty();
        assertThat(history.getAllSeries()).isEmpty();
    }

    @Test
    void shouldAddPointsForDifferentWindowsIndependently() {
        UsageHistory history = new UsageHistory();

        history.add(PROVIDER, UsageWindowType.FIVE_HOUR, 10, T1);
        history.add(PROVIDER, UsageWindowType.SEVEN_DAY, 5, T1);

        assertThat(history.getSeries(PROVIDER, UsageWindowType.FIVE_HOUR)).hasSize(1);
        assertThat(history.getSeries(PROVIDER, UsageWindowType.SEVEN_DAY)).hasSize(1);
    }

    @Test
    void shouldAddFromUsageReading() {
        UsageHistory history = new UsageHistory();
        UsageReading reading = new UsageReading(
                PROVIDER,
                T1,
                List.of(
                        new UsageWindowReading(UsageWindowType.FIVE_HOUR, 10, T2),
                        new UsageWindowReading(UsageWindowType.SEVEN_DAY, 5, T3)
                )
        );

        history.add(reading);

        assertThat(history.getSeries(PROVIDER, UsageWindowType.FIVE_HOUR)).hasSize(1);
        assertThat(history.getSeries(PROVIDER, UsageWindowType.SEVEN_DAY)).hasSize(1);
    }

    @Test
    void shouldReturnImmutableSeries() {
        UsageHistory history = new UsageHistory();
        history.add(PROVIDER, UsageWindowType.FIVE_HOUR, 10, T1);

        List<UsageHistoryPoint> series = history.getSeries(PROVIDER, UsageWindowType.FIVE_HOUR);

        assertThat(series).isUnmodifiable();
    }
}
