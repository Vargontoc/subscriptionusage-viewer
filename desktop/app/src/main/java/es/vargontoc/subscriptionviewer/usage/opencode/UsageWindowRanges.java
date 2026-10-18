package es.vargontoc.subscriptionviewer.usage.opencode;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

/** Calcula las ventanas de uso de OpenCode a partir de un único instante. */
public final class UsageWindowRanges {
    private final Clock clock;
    private final ZoneId zone;
    private final int cycleDay;

    public UsageWindowRanges(Clock clock, ZoneId zone, int cycleDay) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.zone = Objects.requireNonNull(zone, "zone");
        if (cycleDay < 1 || cycleDay > 31) {
            throw new IllegalArgumentException("cycleDay must be between 1 and 31");
        }
        this.cycleDay = cycleDay;
    }

    public static Ranges calculate(Instant now, ZoneId zone, int cycleDay) {
        return new UsageWindowRanges(Clock.fixed(now, zone), zone, cycleDay).calculate();
    }

    /** Calcula las tres ventanas usando el instante del reloj una sola vez. */
    public Ranges calculate() {
        return calculate(clock.instant());
    }

    /** Variante útil para callers que ya han capturado el instante del ciclo. */
    public Ranges calculate(Instant now) {
        Objects.requireNonNull(now, "now");
        ZonedDateTime localNow = now.atZone(zone);

        Instant fiveHoursFrom = now.minusSeconds(5 * 60 * 60);
        Instant sevenDaysFrom = localNow.minusDays(7).toInstant();
        Instant monthlyFrom = cycleStart(localNow).toInstant();
        return new Ranges(
                new Range(fiveHoursFrom, now),
                new Range(sevenDaysFrom, now),
                new Range(monthlyFrom, now));
    }

    public record Range(Instant from, Instant to) {
        public String fromIso8601() { return from.toString(); }
        public String toIso8601() { return to.toString(); }
        public String fromIso() { return from.toString(); }
        public String toIso() { return to.toString(); }
    }

    public record Ranges(Range fiveHours, Range sevenDays, Range month) {
        public Range fiveHour() { return fiveHours; }
        public Range sevenDay() { return sevenDays; }
        public Range monthly() { return month; }
        public Range getFiveHours() { return fiveHours; }
        public Range getSevenDays() { return sevenDays; }
        public Range getMonth() { return month; }
    }

    private ZonedDateTime cycleStart(ZonedDateTime now) {
        YearMonth currentMonth = YearMonth.from(now);
        ZonedDateTime candidate = startOfCycle(currentMonth);
        if (candidate.isAfter(now)) {
            candidate = startOfCycle(currentMonth.minusMonths(1));
        }
        return candidate;
    }

    private ZonedDateTime startOfCycle(YearMonth month) {
        LocalDate date = month.atDay(Math.min(cycleDay, month.lengthOfMonth()));
        return date.atStartOfDay(zone);
    }
}
