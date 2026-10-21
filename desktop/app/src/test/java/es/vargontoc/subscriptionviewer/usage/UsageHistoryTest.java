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
    private static final String OPENCODE_PROVIDER = "opencode";
    private static final String OPENAI_PROVIDER = "openai";
    private static final String GEMINI_PROVIDER = "gemini";
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
    void shouldAddOpenCodePointOnlyWhenPercentageChanges() {
        UsageHistory history = new UsageHistory();
        Instant reset1 = Instant.parse("2026-10-16T15:00:00Z");
        Instant reset2 = Instant.parse("2026-10-16T16:00:00Z");

        history.add(new UsageReading(OPENCODE_PROVIDER, T1, List.of(
                new UsageWindowReading(UsageWindowType.FIVE_HOUR, 10, reset1))));
        history.add(new UsageReading(OPENCODE_PROVIDER, T2, List.of(
                new UsageWindowReading(UsageWindowType.FIVE_HOUR, 20, reset1))));
        history.add(new UsageReading(OPENCODE_PROVIDER, T3, List.of(
                new UsageWindowReading(UsageWindowType.FIVE_HOUR, 20, reset2))));

        assertThat(history.getSeries(OPENCODE_PROVIDER, UsageWindowType.FIVE_HOUR))
                .extracting(UsageHistoryPoint::usedPercentage)
                 .containsExactly(10.0, 20.0);
        assertThat(history.getSeries(OPENCODE_PROVIDER, UsageWindowType.FIVE_HOUR))
                .extracting(UsageHistoryPoint::writtenAt)
                .containsExactly(T1, T2);
    }

    @Test
    void shouldNotDuplicateOpenCodeValuesWhenQueryTimeChanges() {
        UsageHistory history = new UsageHistory();
        Instant reset = Instant.parse("2026-10-16T15:00:00Z");

        history.add(new UsageReading(OPENCODE_PROVIDER, T1, List.of(
                new UsageWindowReading(UsageWindowType.FIVE_HOUR, 10, reset))));
        history.add(new UsageReading(OPENCODE_PROVIDER, T2, List.of(
                new UsageWindowReading(UsageWindowType.FIVE_HOUR, 10, reset))));

        assertThat(history.getSeries(OPENCODE_PROVIDER, UsageWindowType.FIVE_HOUR)).hasSize(1);
    }

    @Test
    void shouldKeepMostRecentOpenCodePointsWhenLimitIsExceeded() {
        UsageHistory history = new UsageHistory();
        Instant reset = Instant.parse("2026-10-16T15:00:00Z");

        for (int i = 0; i < UsageHistory.MAX_POINTS_PER_SERIES + 5; i++) {
            history.add(new UsageReading(OPENCODE_PROVIDER, T1.plusSeconds(i), List.of(
                    new UsageWindowReading(UsageWindowType.FIVE_HOUR, i, reset))));
        }

        assertThat(history.getSeries(OPENCODE_PROVIDER, UsageWindowType.FIVE_HOUR)).hasSize(UsageHistory.MAX_POINTS_PER_SERIES);
        assertThat(history.getSeries(OPENCODE_PROVIDER, UsageWindowType.FIVE_HOUR).get(0).usedPercentage())
                .isEqualTo(1);
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
        assertThat(series.get(series.size() - 1).usedPercentage()).isEqualTo(100);
    }

    @Test
    void shouldBeEmptyWhenCreated() {
        UsageHistory history = new UsageHistory();

        assertThat(history.getSeries(PROVIDER, UsageWindowType.FIVE_HOUR)).isEmpty();
        assertThat(history.getAllSeries()).isEmpty();
    }

    @Test
    void shouldPreserveDecimalGeminiPercentages() {
        UsageHistory history = new UsageHistory();

        history.add(GEMINI_PROVIDER, "Gemini Models", UsageWindowType.FIVE_HOUR, 1.8, T1);

        assertThat(history.getSeries(GEMINI_PROVIDER, "Gemini Models", UsageWindowType.FIVE_HOUR)
                .get(0).usedPercentage()).isEqualTo(1.8);
    }

    @Test
    void shouldAddGeminiPointsPerGroupAndWindowWhenRoundedValueChanges() {
        UsageHistory history = new UsageHistory();

        history.add(GEMINI_PROVIDER, "Gemini Models", UsageWindowType.FIVE_HOUR, 1.8, T1);
        history.add(GEMINI_PROVIDER, "Gemini Models", UsageWindowType.FIVE_HOUR, 1.9, T2);
        history.add(GEMINI_PROVIDER, "Gemini Models", UsageWindowType.SEVEN_DAY, 3.1, T1);
        history.add(GEMINI_PROVIDER, "Claude and GPT models", UsageWindowType.FIVE_HOUR, 4.2, T1);

        assertThat(history.getSeries(GEMINI_PROVIDER, "Gemini Models", UsageWindowType.FIVE_HOUR))
                .extracting(UsageHistoryPoint::usedPercentage)
                .containsExactly(1.8, 1.9);
        assertThat(history.getSeries(GEMINI_PROVIDER, "Gemini Models", UsageWindowType.FIVE_HOUR))
                .extracting(UsageHistoryPoint::writtenAt)
                .containsExactly(T1, T2);
        assertThat(history.getSeries(GEMINI_PROVIDER, "Gemini Models", UsageWindowType.SEVEN_DAY))
                .hasSize(1);
        assertThat(history.getSeries(GEMINI_PROVIDER, "Claude and GPT models", UsageWindowType.FIVE_HOUR))
                .hasSize(1);
    }

    @Test
    void shouldNotDuplicateGeminiValueWhenReadingIsRepeated() {
        UsageHistory history = new UsageHistory();

        history.add(GEMINI_PROVIDER, "Gemini Models", UsageWindowType.FIVE_HOUR, 1.8, T1);
        history.add(GEMINI_PROVIDER, "Gemini Models", UsageWindowType.FIVE_HOUR, 1.8, T2);

        assertThat(history.getSeries(GEMINI_PROVIDER, "Gemini Models", UsageWindowType.FIVE_HOUR))
                .hasSize(1);
    }

    @Test
    void shouldTreatGeminiValuesEqualAfterRoundingAsTheSame() {
        UsageHistory history = new UsageHistory();

        history.add(GEMINI_PROVIDER, "Gemini Models", UsageWindowType.FIVE_HOUR, 1.84, T1);
        history.add(GEMINI_PROVIDER, "Gemini Models", UsageWindowType.FIVE_HOUR, 1.76, T2);

        assertThat(history.getSeries(GEMINI_PROVIDER, "Gemini Models", UsageWindowType.FIVE_HOUR))
                .extracting(UsageHistoryPoint::usedPercentage)
                .containsExactly(1.84);
    }

    @Test
    void shouldKeepSeparateGeminiPointsWhenRoundedValuesDiffer() {
        UsageHistory history = new UsageHistory();

        history.add(GEMINI_PROVIDER, "Gemini Models", UsageWindowType.FIVE_HOUR, 1.86, T1);
        history.add(GEMINI_PROVIDER, "Gemini Models", UsageWindowType.FIVE_HOUR, 1.84, T2);

        assertThat(history.getSeries(GEMINI_PROVIDER, "Gemini Models", UsageWindowType.FIVE_HOUR))
                .extracting(UsageHistoryPoint::usedPercentage)
                .containsExactly(1.86, 1.84);
    }

    @Test
    void shouldDeduplicateClaudeOpenCodeAndOpenAiIntegerValues() {
        UsageHistory history = new UsageHistory();

        history.add(PROVIDER, UsageWindowType.FIVE_HOUR, 10, T1);
        history.add(PROVIDER, UsageWindowType.FIVE_HOUR, 10, T1);
        history.add(OPENCODE_PROVIDER, UsageWindowType.FIVE_HOUR, 10, T1);
        history.add(OPENCODE_PROVIDER, UsageWindowType.FIVE_HOUR, 10, T2);
        history.add(OPENAI_PROVIDER, UsageWindowType.FIVE_HOUR, 10, T1);
        history.add(OPENAI_PROVIDER, UsageWindowType.FIVE_HOUR, 10, T2);

        assertThat(history.getSeries(PROVIDER, UsageWindowType.FIVE_HOUR)).hasSize(1);
        assertThat(history.getSeries(OPENCODE_PROVIDER, UsageWindowType.FIVE_HOUR)).hasSize(1);
        assertThat(history.getSeries(OPENAI_PROVIDER, UsageWindowType.FIVE_HOUR)).hasSize(1);
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
    void shouldAddChangedOpenAiPercentagesForEachWindowInChronologicalOrder() {
        UsageHistory history = new UsageHistory();
        Instant reset1 = T1.plusSeconds(3600);
        Instant reset2 = T2.plusSeconds(3600);
        Instant reset3 = T3.plusSeconds(3600);

        history.add(new UsageReading(OPENAI_PROVIDER, T1, List.of(
                new UsageWindowReading(UsageWindowType.FIVE_HOUR, 10, reset1),
                new UsageWindowReading(UsageWindowType.SEVEN_DAY, 2, reset1))));
        history.add(new UsageReading(OPENAI_PROVIDER, T2, List.of(
                new UsageWindowReading(UsageWindowType.FIVE_HOUR, 20, reset2),
                new UsageWindowReading(UsageWindowType.SEVEN_DAY, 3, reset2))));
        history.add(new UsageReading(OPENAI_PROVIDER, T3, List.of(
                new UsageWindowReading(UsageWindowType.FIVE_HOUR, 30, reset3),
                new UsageWindowReading(UsageWindowType.SEVEN_DAY, 4, reset3))));

        assertThat(history.getSeries(OPENAI_PROVIDER, UsageWindowType.FIVE_HOUR))
                .extracting(UsageHistoryPoint::usedPercentage)
                 .containsExactly(10.0, 20.0, 30.0);
        assertThat(history.getSeries(OPENAI_PROVIDER, UsageWindowType.FIVE_HOUR))
                .extracting(UsageHistoryPoint::writtenAt)
                .containsExactly(T1, T2, T3);
        assertThat(history.getSeries(OPENAI_PROVIDER, UsageWindowType.SEVEN_DAY))
                .extracting(UsageHistoryPoint::usedPercentage)
                 .containsExactly(2.0, 3.0, 4.0);
    }

    @Test
    void shouldNotDuplicateOpenAiValueWhenQueryTimeOrResetChanges() {
        UsageHistory history = new UsageHistory();
        Instant reset1 = Instant.parse("2026-10-16T15:00:00Z");
        Instant reset2 = Instant.parse("2026-10-16T16:00:00Z");

        history.add(new UsageReading(OPENAI_PROVIDER, T1, List.of(
                new UsageWindowReading(UsageWindowType.FIVE_HOUR, 10, reset1),
                new UsageWindowReading(UsageWindowType.SEVEN_DAY, 20, reset1))));
        history.add(new UsageReading(OPENAI_PROVIDER, T2, List.of(
                new UsageWindowReading(UsageWindowType.FIVE_HOUR, 10, reset2),
                new UsageWindowReading(UsageWindowType.SEVEN_DAY, 20, reset2))));

        assertThat(history.getSeries(OPENAI_PROVIDER, UsageWindowType.FIVE_HOUR)).hasSize(1);
        assertThat(history.getSeries(OPENAI_PROVIDER, UsageWindowType.SEVEN_DAY)).hasSize(1);
    }

    @Test
    void shouldPreserveClaudeHistoryWhenPercentageIsUnchangedButTimeAdvances() {
        UsageHistory history = new UsageHistory();
        Instant reset1 = T1.plusSeconds(3600);
        Instant reset2 = T2.plusSeconds(3600);

        history.add(new UsageReading(PROVIDER, T1, List.of(
                new UsageWindowReading(UsageWindowType.FIVE_HOUR, 40, reset1))));
        history.add(new UsageReading(PROVIDER, T2, List.of(
                new UsageWindowReading(UsageWindowType.FIVE_HOUR, 40, reset2))));

        assertThat(history.getSeries(PROVIDER, UsageWindowType.FIVE_HOUR))
                .extracting(UsageHistoryPoint::usedPercentage)
                 .containsExactly(40.0, 40.0);
        assertThat(history.getSeries(PROVIDER, UsageWindowType.FIVE_HOUR))
                .extracting(UsageHistoryPoint::writtenAt)
                .containsExactly(T1, T2);
    }

    @Test
    void shouldKeepIndependentSeriesForModelsAndTotal() {
        UsageHistory history = new UsageHistory();

        history.add(OPENCODE_PROVIDER, "model-a", UsageWindowType.FIVE_HOUR, 10, T1);
        history.add(OPENCODE_PROVIDER, "model-b", UsageWindowType.FIVE_HOUR, 20, T1);
        history.add(OPENCODE_PROVIDER, UsageHistory.TOTAL_SERIES, UsageWindowType.FIVE_HOUR, 15, T1);

        assertThat(history.getSeries(OPENCODE_PROVIDER, "model-a", UsageWindowType.FIVE_HOUR))
                 .extracting(UsageHistoryPoint::usedPercentage).containsExactly(10.0);
        assertThat(history.getSeries(OPENCODE_PROVIDER, "model-b", UsageWindowType.FIVE_HOUR))
                 .extracting(UsageHistoryPoint::usedPercentage).containsExactly(20.0);
        assertThat(history.getSeries(OPENCODE_PROVIDER, UsageHistory.TOTAL_SERIES, UsageWindowType.FIVE_HOUR))
                 .extracting(UsageHistoryPoint::usedPercentage).containsExactly(15.0);
        assertThat(history.getSeries(OPENCODE_PROVIDER, UsageWindowType.FIVE_HOUR)).isEmpty();
    }

    @Test
    void shouldStartLateModelSeriesAtItsFirstReading() {
        UsageHistory history = new UsageHistory();

        history.add(OPENCODE_PROVIDER, "model-a", UsageWindowType.FIVE_HOUR, 10, T1);
        history.add(OPENCODE_PROVIDER, "model-a", UsageWindowType.FIVE_HOUR, 20, T2);
        history.add(OPENCODE_PROVIDER, "model-b", UsageWindowType.FIVE_HOUR, 30, T3);

        assertThat(history.getSeries(OPENCODE_PROVIDER, "model-b", UsageWindowType.FIVE_HOUR))
                .extracting(UsageHistoryPoint::writtenAt).containsExactly(T3);
    }

    @Test
    void shouldSaturateHistoryPointsAtOneHundred() {
        UsageHistory history = new UsageHistory();

        history.add(OPENCODE_PROVIDER, "model-a", UsageWindowType.FIVE_HOUR, 150, T1);

        assertThat(history.getSeries(OPENCODE_PROVIDER, "model-a", UsageWindowType.FIVE_HOUR).get(0)
                .usedPercentage()).isEqualTo(100);
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
    void shouldStoreOpenCodeAggregateReadingInTotalSeries() {
        UsageHistory history = new UsageHistory();

        history.add(new UsageReading(OPENCODE_PROVIDER, T1, List.of(
                new UsageWindowReading(UsageWindowType.FIVE_HOUR, 25, null))));

        assertThat(history.getSeries(OPENCODE_PROVIDER, UsageHistory.TOTAL_SERIES,
                UsageWindowType.FIVE_HOUR))
                .extracting(UsageHistoryPoint::usedPercentage)
                 .containsExactly(25.0);
        assertThat(history.getSeries(OPENCODE_PROVIDER, UsageWindowType.FIVE_HOUR))
                .extracting(UsageHistoryPoint::usedPercentage)
                 .containsExactly(25.0);
    }

    @Test
    void shouldReturnImmutableSeries() {
        UsageHistory history = new UsageHistory();
        history.add(PROVIDER, UsageWindowType.FIVE_HOUR, 10, T1);

        List<UsageHistoryPoint> series = history.getSeries(PROVIDER, UsageWindowType.FIVE_HOUR);

        assertThat(series).isUnmodifiable();
    }
}
