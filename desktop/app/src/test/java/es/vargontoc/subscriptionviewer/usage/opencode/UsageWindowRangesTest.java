package es.vargontoc.subscriptionviewer.usage.opencode;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class UsageWindowRangesTest {
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @Test
    void calculatesAllWindowsFromTheSameNow() {
        UsageWindowRanges.Ranges ranges = calculator("2026-10-17T12:00:00Z", 1).calculate();

        assertThat(ranges.fiveHours()).isEqualTo(range("2026-10-17T07:00:00Z", "2026-10-17T12:00:00Z"));
        assertThat(ranges.sevenDays()).isEqualTo(range("2026-10-10T12:00:00Z", "2026-10-17T12:00:00Z"));
        assertThat(ranges.month()).isEqualTo(range("2026-09-30T22:00:00Z", "2026-10-17T12:00:00Z"));
    }

    @Test
    void usesPreviousCycleBeforeTheConfiguredDayAndCurrentCycleAfterIt() {
        assertThat(calculator("2026-10-17T12:00:00Z", 20).calculate().month())
                .isEqualTo(range("2026-09-19T22:00:00Z", "2026-10-17T12:00:00Z"));
        assertThat(calculator("2026-10-25T12:00:00Z", 20).calculate().month())
                .isEqualTo(range("2026-10-19T22:00:00Z", "2026-10-25T12:00:00Z"));
    }

    @Test
    void clampsCycleDayToShortMonths() {
        assertThat(calculator("2026-03-05T09:00:00Z", 31).calculate().month())
                .isEqualTo(range("2026-02-27T23:00:00Z", "2026-03-05T09:00:00Z"));
        assertThat(calculator("2026-04-30T08:00:00Z", 31).calculate().month())
                .isEqualTo(range("2026-04-29T22:00:00Z", "2026-04-30T08:00:00Z"));
    }

    @Test
    void includesExactCycleStartAsTheStart() {
        UsageWindowRanges.Ranges ranges = calculator("2026-10-20T22:00:00Z", 20).calculate();
        assertThat(ranges.month().from()).isEqualTo(Instant.parse("2026-10-19T22:00:00Z"));
    }

    @Test
    void exposesWindowBoundsAsUnixMilliseconds() {
        UsageWindowRanges.Range range = new UsageWindowRanges.Range(
                Instant.parse("2026-10-18T00:00:00Z"), Instant.parse("2026-10-19T00:00:00Z"));

        assertThat(range.fromUnixMillis()).isEqualTo("1792281600000");
        assertThat(range.toUnixMillis()).isEqualTo("1792368000000");
    }

    private UsageWindowRanges calculator(String instant, int day) {
        return new UsageWindowRanges(Clock.fixed(Instant.parse(instant), ZoneId.of("UTC")), MADRID, day);
    }

    private UsageWindowRanges.Range range(String from, String to) {
        return new UsageWindowRanges.Range(Instant.parse(from), Instant.parse(to));
    }
}
