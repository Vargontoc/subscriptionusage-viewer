package es.vargontoc.subscriptionviewer.usage;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas unitarias del contrato {@link UsageSource}.
 *
 * <p>Verifica que una fuente devuelve lecturas con las ventanas esperadas y que
 * cualquier fallo o ausencia se encapsula sin propagar excepciones al consumidor.</p>
 */
class UsageSourceTest {

    private static final Instant NOW = Instant.parse("2026-10-16T10:32:15Z");

    @Test
    void shouldReturnReadingWithBothWindows() {
        UsageSource source = new FixedUsageSource("test-provider", 40, 15);

        UsageReadResult result = source.read();

        assertThat(result.estado()).isEqualTo(UsageReadResult.Estado.DATOS);
        UsageReading reading = result.get();
        assertThat(reading.providerId()).isEqualTo("test-provider");
        assertThat(reading.writtenAt()).isEqualTo(NOW);
        assertThat(reading.windows()).hasSize(2);
        assertThat(reading.windows().get(0).type()).isEqualTo(UsageWindowType.FIVE_HOUR);
        assertThat(reading.windows().get(0).usedPercentage()).isEqualTo(40);
        assertThat(reading.windows().get(0).resetsAt()).isEqualTo(NOW.plusSeconds(3600));
        assertThat(reading.windows().get(1).type()).isEqualTo(UsageWindowType.SEVEN_DAY);
        assertThat(reading.windows().get(1).usedPercentage()).isEqualTo(15);
        assertThat(reading.windows().get(1).resetsAt()).isEqualTo(NOW.plusSeconds(86400));
    }

    @Test
    void shouldReturnEmptyWhenSourceReturnsEmpty() {
        UsageSource source = new EmptyUsageSource("empty-provider");

        UsageReadResult result = source.read();

        assertThat(result.estado()).isEqualTo(UsageReadResult.Estado.FALLO_TRANSITORIO);
    }

    @Test
    void shouldReturnEmptyWhenSourceThrows() {
        UsageSource source = new ThrowingUsageSource("failing-provider");

        UsageReadResult result = source.read();

        assertThat(result.estado()).isEqualTo(UsageReadResult.Estado.FALLO_TRANSITORIO);
    }

    @Test
    void shouldExposeExpiredSessionAsDistinctResult() {
        UsageSource source = new ExpiredSessionUsageSource();

        UsageReadResult result = source.read();

        assertThat(result.estado()).isEqualTo(UsageReadResult.Estado.SESION_CADUCADA);
        assertThat(result.estado()).isNotEqualTo(UsageReadResult.Estado.FALLO_TRANSITORIO);
        assertThat(result.estado()).isNotEqualTo(UsageReadResult.Estado.DATOS);
        assertThat(result.datos()).isEmpty();
        assertThat(result.causa()).isEmpty();
    }

    @Test
    void shouldNotAffectOtherSourcesWhenOneFails() {
        List<UsageSource> sources = List.of(
                new ThrowingUsageSource("failing"),
                new EmptyUsageSource("empty"),
                new FixedUsageSource("working", 40, 15)
        );

        List<UsageReading> readings = new ArrayList<>();
        for (UsageSource source : sources) {
            source.read().ifPresent(readings::add);
        }

        assertThat(readings).hasSize(1);
        assertThat(readings.get(0).providerId()).isEqualTo("working");
    }


    private static class FixedUsageSource implements UsageSource {
        private final String providerId;
        private final int fiveHourPercentage;
        private final int sevenDayPercentage;

        FixedUsageSource(String providerId, int fiveHourPercentage, int sevenDayPercentage) {
            this.providerId = providerId;
            this.fiveHourPercentage = fiveHourPercentage;
            this.sevenDayPercentage = sevenDayPercentage;
        }

        @Override
        public String getProviderId() {
            return providerId;
        }

        @Override
        public Optional<UsageReading> doRead() {
            return Optional.of(new UsageReading(
                    providerId,
                    NOW,
                    List.of(
                            new UsageWindowReading(UsageWindowType.FIVE_HOUR, fiveHourPercentage, NOW.plusSeconds(3600)),
                            new UsageWindowReading(UsageWindowType.SEVEN_DAY, sevenDayPercentage, NOW.plusSeconds(86400))
                    )
            ));
        }
    }

    private static class EmptyUsageSource implements UsageSource {
        private final String providerId;

        EmptyUsageSource(String providerId) {
            this.providerId = providerId;
        }

        @Override
        public String getProviderId() {
            return providerId;
        }

        @Override
        public Optional<UsageReading> doRead() {
            return Optional.empty();
        }
    }

    private static class ThrowingUsageSource implements UsageSource {
        private final String providerId;

        ThrowingUsageSource(String providerId) {
            this.providerId = providerId;
        }

        @Override
        public String getProviderId() {
            return providerId;
        }

        @Override
        public Optional<UsageReading> doRead() {
            throw new RuntimeException("Simulated failure");
        }
    }

    private static class ExpiredSessionUsageSource implements UsageSource {
        @Override
        public String getProviderId() {
            return "expired-provider";
        }

        @Override
        public UsageReadResult doReadResult() {
            return UsageReadResult.sesionCaducada();
        }

        @Override
        public Optional<UsageReading> doRead() {
            return Optional.empty();
        }
    }

}
