package es.vargontoc.subscriptionviewer.usage;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UsageHistoryPointTest {

    @Test
    void shouldPreserveDecimalPercentage() {
        UsageHistoryPoint point = new UsageHistoryPoint(1.8, Instant.EPOCH);

        assertThat(point.usedPercentage()).isEqualTo(1.8);
    }

    @Test
    void shouldRepresentBoundsAsDoubleValues() {
        assertThat(new UsageHistoryPoint(0, Instant.EPOCH).usedPercentage()).isEqualTo(0.0);
        assertThat(new UsageHistoryPoint(100, Instant.EPOCH).usedPercentage()).isEqualTo(100.0);
    }
}
