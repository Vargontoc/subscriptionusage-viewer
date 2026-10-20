package es.vargontoc.subscriptionviewer.usage;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UsageWindowReadingTest {

    @Test
    void shouldPreserveDecimalPercentage() {
        UsageWindowReading reading = new UsageWindowReading(UsageWindowType.WEEKLY, 1.8, Instant.EPOCH);

        assertThat(reading.usedPercentage()).isEqualTo(1.8);
    }

    @Test
    void shouldRepresentZeroAndOneHundredAsDoubleValues() {
        assertThat(new UsageWindowReading(UsageWindowType.WEEKLY, 0, Instant.EPOCH).usedPercentage())
                .isEqualTo(0.0);
        assertThat(new UsageWindowReading(UsageWindowType.WEEKLY, 100, Instant.EPOCH).usedPercentage())
                .isEqualTo(100.0);
    }
}
