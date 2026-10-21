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

        OpenCodeUsageCalculator.ModelUsage a = result.models().stream()
                .filter(model -> model.model().equals("model-a"))
                .findFirst().orElseThrow();
        assertThat(a.percentage(UsageWindowType.FIVE_HOUR)).isEqualByComparingTo("50");
        assertThat(a.percentage(UsageWindowType.SEVEN_DAY)).isEqualByComparingTo("50");
        assertThat(a.percentage(UsageWindowType.MONTHLY)).isEqualByComparingTo("75");

        OpenCodeUsageCalculator.Calculation total = calculator.calculate(
                List.of(model("model-a", "6"), model("model-b", "4")), List.of(), List.of());
        assertThat(total.total().percentage(UsageWindowType.FIVE_HOUR))
                .isEqualByComparingTo("31.25");
    }

    @Test
    void preservesDecimalPercentageBelowTheSaturationLimit() {
        OpenCodeUsageCalculator.Calculation result = new OpenCodeUsageCalculator().calculate(
                List.of(), List.of(), List.of(model("model-a", "59.976")));

        assertThat(result.models().getFirst().percentage(UsageWindowType.MONTHLY))
                .isEqualByComparingTo("99.96");
        assertThat(result.total().percentage(UsageWindowType.MONTHLY))
                .isEqualByComparingTo("99.96");
    }

    @Test
    void sanitizesCostsUsesDefaultCapAndSaturates() {
        OpenCodeUsageCalculator.Calculation result = new OpenCodeUsageCalculator().calculate(
                List.of(model("marker", "-1"), model("listed", "15"), model("empty", null)),
                List.of(), List.of());

        assertThat(result.models()).extracting(OpenCodeUsageCalculator.ModelUsage::model)
                .containsExactly("marker", "listed", "empty");
        assertThat(result.models().get(1).percentage(UsageWindowType.FIVE_HOUR))
                .isEqualByComparingTo("100");
        assertThat(result.total().percentage(UsageWindowType.FIVE_HOUR))
                .isEqualByComparingTo("100");
        assertThat(result.total().percentage(UsageWindowType.SEVEN_DAY))
                .isEqualByComparingTo("0");
    }

    @Test
    void assignsMinimumOnePercentToSmallModelAndTotalConsumption() {
        OpenCodeUsageCalculator.Calculation result = new OpenCodeUsageCalculator().calculate(
                List.of(), List.of(), List.of(model("small", "0.01")));

        assertThat(result.models().getFirst().percentage(UsageWindowType.MONTHLY))
                .isEqualByComparingTo("1");
        assertThat(result.total().percentage(UsageWindowType.MONTHLY))
                .isEqualByComparingTo("1");
    }

    @Test
    void includesConfiguredModelWithoutCliConsumption() {
        OpenCodeUsageCalculator calculator = new OpenCodeUsageCalculator(
                Map.of("configured", new BigDecimal("80")), new BigDecimal("60"));

        OpenCodeUsageCalculator.Calculation result = calculator.calculate(
                List.of(), List.of(), List.of());

        assertThat(result.models()).extracting(OpenCodeUsageCalculator.ModelUsage::model)
                .containsExactly("configured");
        OpenCodeUsageCalculator.ModelUsage model = result.models().getFirst();
        assertThat(model.capUsd()).isEqualByComparingTo("80");
        assertThat(model.percentage(UsageWindowType.FIVE_HOUR)).isZero();
        assertThat(model.percentage(UsageWindowType.SEVEN_DAY)).isZero();
        assertThat(model.percentage(UsageWindowType.MONTHLY)).isZero();
        assertThat(result.total().percentage(UsageWindowType.MONTHLY)).isZero();
    }

    @Test
    void includesJsonModelNotConfiguredWithDefaultCapAndAggregatesBothSources() {
        OpenCodeUsageCalculator calculator = new OpenCodeUsageCalculator(
                Map.of("configured", new BigDecimal("80")), new BigDecimal("60"));

        OpenCodeUsageCalculator.Calculation result = calculator.calculate(
                List.of(model("from-json", "6")), List.of(), List.of());

        assertThat(result.models()).extracting(OpenCodeUsageCalculator.ModelUsage::model)
                .containsExactly("configured", "from-json");
        OpenCodeUsageCalculator.ModelUsage jsonModel = result.models().get(1);
        assertThat(jsonModel.capUsd()).isEqualByComparingTo("60");
        assertThat(result.total().percentage(UsageWindowType.FIVE_HOUR))
                .isEqualByComparingTo("50");
    }

    private static OpenCodeModelStats model(String id, String cost) {
        return new OpenCodeModelStats(id, "opencode-go", "default", 1, TOKENS,
                cost == null ? null : new BigDecimal(cost));
    }
}
