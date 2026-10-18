package es.vargontoc.subscriptionviewer.usage.opencode;

import es.vargontoc.subscriptionviewer.usage.UsageWindowType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenCodeUsageCalculatorTest {
    private static final OpenCodeTokens TOKENS = new OpenCodeTokens(0, 0, 0, 0, 0);

    @Test
    void calculatesModelPercentagesAndAggregatedTotal() {
        OpenCodeUsageCalculator calculator = new OpenCodeUsageCalculator(
                Map.of("model-b", new BigDecimal("100")), new BigDecimal("60"));
        OpenCodeUsageCalculator.Calculation result = calculator.calculate(
                List.of(model("model-a", "6"), model("model-a", "0")),
                List.of(model("model-a", "15")),
                List.of(model("model-a", "45")));

        OpenCodeUsageCalculator.ModelUsage a = result.models().getFirst();
        assertThat(a.percentage(UsageWindowType.FIVE_HOUR)).isEqualByComparingTo("50");
        assertThat(a.percentage(UsageWindowType.SEVEN_DAY)).isEqualByComparingTo("50");
        assertThat(a.percentage(UsageWindowType.MONTHLY)).isEqualByComparingTo("75");

        OpenCodeUsageCalculator.Calculation total = calculator.calculate(
                List.of(model("model-a", "6"), model("model-b", "4")), List.of(), List.of());
        assertThat(total.total().percentage(UsageWindowType.FIVE_HOUR))
                .isEqualByComparingTo("31.25");
    }

    @Test
    void sanitizesCostsUsesDefaultCapAndSaturates() {
        OpenCodeUsageCalculator.Calculation result = new OpenCodeUsageCalculator().calculate(
                List.of(model("marker", "-1"), model("listed", "15"), model("empty", null)),
                List.of(), List.of());

        assertThat(result.models()).extracting(OpenCodeUsageCalculator.ModelUsage::model)
                .containsExactly("listed");
        assertThat(result.models().getFirst().percentage(UsageWindowType.FIVE_HOUR))
                .isEqualByComparingTo("100");
        assertThat(result.total().percentage(UsageWindowType.SEVEN_DAY))
                .isEqualByComparingTo("0");
    }

    private static OpenCodeModelStats model(String id, String cost) {
        return new OpenCodeModelStats(id, "opencode-go", "default", 1, TOKENS,
                cost == null ? null : new BigDecimal(cost));
    }
}
