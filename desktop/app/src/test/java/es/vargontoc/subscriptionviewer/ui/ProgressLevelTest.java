package es.vargontoc.subscriptionviewer.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ProgressLevelTest {

    @Test
    void shouldClassifyAllRequiredThresholdsWithoutRounding() {
        assertThat(ProgressLevel.of(0.0)).isEqualTo(ProgressLevel.VERDE);
        assertThat(ProgressLevel.of(25.0)).isEqualTo(ProgressLevel.VERDE);
        assertThat(ProgressLevel.of(50.0)).isEqualTo(ProgressLevel.VERDE);
        assertThat(ProgressLevel.of(50.1)).isEqualTo(ProgressLevel.AMARILLO);
        assertThat(ProgressLevel.of(75.0)).isEqualTo(ProgressLevel.AMARILLO);
        assertThat(ProgressLevel.of(75.01)).isEqualTo(ProgressLevel.NARANJA);
        assertThat(ProgressLevel.of(90.0)).isEqualTo(ProgressLevel.NARANJA);
        assertThat(ProgressLevel.of(90.01)).isEqualTo(ProgressLevel.ROJO);
        assertThat(ProgressLevel.of(99.0)).isEqualTo(ProgressLevel.ROJO);
        assertThat(ProgressLevel.of(99.5)).isEqualTo(ProgressLevel.ROJO);
        assertThat(ProgressLevel.of(99.96)).isEqualTo(ProgressLevel.ROJO);
        assertThat(ProgressLevel.of(100.0)).isEqualTo(ProgressLevel.NEGRO);
    }

    @Test
    void shouldRejectPercentagesOutsideTheAllowedRange() {
        assertThatIllegalArgumentException().isThrownBy(() -> ProgressLevel.of(-0.01));
        assertThatIllegalArgumentException().isThrownBy(() -> ProgressLevel.of(100.01));
        assertThatIllegalArgumentException().isThrownBy(() -> ProgressLevel.of(Double.NaN));
        assertThatIllegalArgumentException().isThrownBy(() -> ProgressLevel.of(Double.POSITIVE_INFINITY));
    }
}
