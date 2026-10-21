package es.vargontoc.subscriptionviewer.usage;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas unitarias de {@link CountdownEvaluator}.
 */
class CountdownEvaluatorTest {

    private static final Instant NOW = Instant.parse("2026-10-16T10:00:00Z");
    private static final Instant RESETS_AT = NOW.plus(Duration.ofHours(2).plusMinutes(10));

    @Test
    void shouldReturnTwoHoursAndTenMinutesWhenResetIsInTheFuture() {
        Clock clock = Clock.fixed(NOW, ZoneId.systemDefault());
        CountdownEvaluator evaluator = new CountdownEvaluator(clock);

        CountdownResult result = evaluator.evaluate(RESETS_AT);

        assertThat(result.valid()).isTrue();
        assertThat(result.remainingTime()).isEqualTo(Duration.ofHours(2).plusMinutes(10));
    }

    @Test
    void shouldReturnTwoHoursAndNineMinutesAfterOneMinutePasses() {
        Clock clock = Clock.fixed(NOW.plus(Duration.ofMinutes(1)), ZoneId.systemDefault());
        CountdownEvaluator evaluator = new CountdownEvaluator(clock);

        CountdownResult result = evaluator.evaluate(RESETS_AT);

        assertThat(result.valid()).isTrue();
        assertThat(result.remainingTime()).isEqualTo(Duration.ofHours(2).plusMinutes(9));
    }

    @Test
    void shouldReturnNotValidWhenWindowHasExpired() {
        Clock clock = Clock.fixed(RESETS_AT.plus(Duration.ofSeconds(1)), ZoneId.systemDefault());
        CountdownEvaluator evaluator = new CountdownEvaluator(clock);

        CountdownResult result = evaluator.evaluate(RESETS_AT);

        assertThat(result.valid()).isFalse();
        assertThat(result.remainingTime()).isEqualTo(Duration.ZERO);
    }

    @Test
    void shouldReturnValidWhenWindowIsInTheFuture() {
        Clock clock = Clock.fixed(NOW, ZoneId.systemDefault());
        CountdownEvaluator evaluator = new CountdownEvaluator(clock);

        CountdownResult result = evaluator.evaluate(NOW.plus(Duration.ofDays(7)));

        assertThat(result.valid()).isTrue();
        assertThat(result.remainingTime()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void shouldFormatLongWindowInDays() {
        Clock clock = Clock.fixed(NOW, ZoneId.systemDefault());
        CountdownEvaluator evaluator = new CountdownEvaluator(clock);

        CountdownResult result = evaluator.evaluate(NOW.plus(Duration.ofDays(2)));

        assertThat(result.valid()).isTrue();
        assertThat(result.formattedRemainingTime()).isEqualTo("2 días");
    }

    @Test
    void shouldKeepShortWindowHoursAndMinutesFormat() {
        Clock clock = Clock.fixed(NOW, ZoneId.systemDefault());
        CountdownEvaluator evaluator = new CountdownEvaluator(clock);

        CountdownResult result = evaluator.evaluate(NOW.plus(Duration.ofHours(3)));

        assertThat(result.formattedRemainingTime()).isEqualTo("3h 0m");
    }

    @Test
    void shouldTreatMissingWindowAsExpired() {
        Clock clock = Clock.fixed(NOW, ZoneId.systemDefault());
        CountdownEvaluator evaluator = new CountdownEvaluator(clock);

        CountdownResult result = evaluator.evaluate(null);

        assertThat(result.valid()).isFalse();
        assertThat(result.remainingTime()).isEqualTo(Duration.ZERO);
        assertThat(result.formattedRemainingTime()).isEmpty();
    }

    @Test
    void shouldReturnZeroWhenResetIsExactlyNow() {
        Clock clock = Clock.fixed(RESETS_AT, ZoneId.systemDefault());
        CountdownEvaluator evaluator = new CountdownEvaluator(clock);

        CountdownResult result = evaluator.evaluate(RESETS_AT);

        assertThat(result.valid()).isFalse();
        assertThat(result.remainingTime()).isEqualTo(Duration.ZERO);
    }

    @Test
    void shouldKeepTwoHoursForOpenAiWhenResetIsInTheFuture() {
        Clock clock = Clock.fixed(NOW, ZoneId.systemDefault());
        CountdownEvaluator evaluator = new CountdownEvaluator(clock);

        CountdownResult result = evaluator.evaluate("openai", NOW.plus(Duration.ofHours(2)));

        assertThat(result.valid()).isTrue();
        assertThat(result.remainingTime()).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void shouldReturnNotValidWhenOpenAiResetHasPassed() {
        Clock clock = Clock.fixed(NOW, ZoneId.systemDefault());
        CountdownEvaluator evaluator = new CountdownEvaluator(clock);

        CountdownResult result = evaluator.evaluate("openai", NOW.minusSeconds(1));

        assertThat(result.valid()).isFalse();
        assertThat(result.remainingTime()).isEqualTo(Duration.ZERO);
        assertThat(result.remainingTime().isNegative()).isFalse();
    }

    @Test
    void shouldReturnTwoHoursForGeminiWhenResetIsInTheFuture() {
        Clock clock = Clock.fixed(NOW, ZoneId.systemDefault());
        CountdownEvaluator evaluator = new CountdownEvaluator(clock);

        CountdownResult result = evaluator.evaluate("gemini", NOW.plus(Duration.ofHours(2)));

        assertThat(result.valid()).isTrue();
        assertThat(result.hasKnownReset()).isTrue();
        assertThat(result.remainingTime()).isEqualTo(Duration.ofHours(2));
        assertThat(result.formattedRemainingTime()).isEqualTo("2h 0m");
    }

    @Test
    void shouldReturnZeroButKeepGeminiWindowVisibleWhenResetHasPassed() {
        Clock clock = Clock.fixed(NOW, ZoneId.systemDefault());
        CountdownEvaluator evaluator = new CountdownEvaluator(clock);

        CountdownResult result = evaluator.evaluate("gemini", NOW.minusSeconds(1));

        assertThat(result.valid()).isTrue();
        assertThat(result.hasKnownReset()).isTrue();
        assertThat(result.remainingTime()).isEqualTo(Duration.ZERO);
        assertThat(result.formattedRemainingTime()).isEqualTo("0s");
    }

    @Test
    void shouldHandleGeminiWindowWithoutReset() {
        Clock clock = Clock.fixed(NOW, ZoneId.systemDefault());
        CountdownEvaluator evaluator = new CountdownEvaluator(clock);

        CountdownResult result = evaluator.evaluate("gemini", null);

        assertThat(result.valid()).isTrue();
        assertThat(result.hasKnownReset()).isFalse();
        assertThat(result.remainingTime()).isEqualTo(Duration.ZERO);
        assertThat(result.formattedRemainingTime()).isEmpty();
    }

    @Test
    void shouldKeepExpiredWindowSemanticsForOtherProvidersWithNullReset() {
        Clock clock = Clock.fixed(NOW, ZoneId.systemDefault());
        CountdownEvaluator evaluator = new CountdownEvaluator(clock);

        CountdownResult claude = evaluator.evaluate("claude", null);
        CountdownResult openCode = evaluator.evaluate("opencode", null);
        CountdownResult openAi = evaluator.evaluate("openai", null);

        assertThat(claude.valid()).isFalse();
        assertThat(claude.hasKnownReset()).isFalse();
        assertThat(openCode.valid()).isFalse();
        assertThat(openCode.hasKnownReset()).isFalse();
        assertThat(openAi.valid()).isFalse();
        assertThat(openAi.hasKnownReset()).isFalse();
    }

    @Test
    void shouldKeepExpiredWindowSemanticsForClaudeAndOpenCode() {
        Clock clock = Clock.fixed(NOW, ZoneId.systemDefault());
        CountdownEvaluator evaluator = new CountdownEvaluator(clock);
        Instant past = NOW.minusSeconds(1);

        CountdownResult claude = evaluator.evaluate("claude", past);
        CountdownResult openCode = evaluator.evaluate("opencode", past);

        assertThat(claude.valid()).isFalse();
        assertThat(openCode.valid()).isFalse();
        assertThat(claude.remainingTime()).isEqualTo(Duration.ZERO);
        assertThat(openCode.remainingTime()).isEqualTo(Duration.ZERO);
    }
}
