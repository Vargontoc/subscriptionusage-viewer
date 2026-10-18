package es.vargontoc.subscriptionviewer.usage.opencode;

import es.vargontoc.subscriptionviewer.config.ConfigurationProvider;
import es.vargontoc.subscriptionviewer.config.AppProperties;
import es.vargontoc.subscriptionviewer.usage.UsageWindowType;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Calcula el consumo de OpenCode sin redondear los valores de dominio. */
public final class OpenCodeUsageCalculator {
    private static final BigDecimal DEFAULT_CAP = BigDecimal.valueOf(60);
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;

    private final Map<String, BigDecimal> modelCaps;
    private final BigDecimal defaultCap;

    public OpenCodeUsageCalculator() {
        this(Map.of(), DEFAULT_CAP);
    }

    public OpenCodeUsageCalculator(ConfigurationProvider configuration) {
        this(configuration.getOpenCodeModelCaps(), configuration.getOpenCodeDefaultCapUsd());
    }

    public OpenCodeUsageCalculator(List<AppProperties.ModelCap> caps, BigDecimal defaultCap) {
        Map<String, BigDecimal> resolved = new LinkedHashMap<>();
        if (caps != null) {
            for (AppProperties.ModelCap entry : caps) {
                if (entry != null && entry.model() != null && entry.capUsd() != null
                        && entry.capUsd().signum() > 0) {
                    resolved.putIfAbsent(entry.model(), entry.capUsd());
                }
            }
        }
        this.modelCaps = Map.copyOf(resolved);
        this.defaultCap = positive(defaultCap) ? defaultCap : DEFAULT_CAP;
    }

    public OpenCodeUsageCalculator(Map<String, BigDecimal> modelCaps, BigDecimal defaultCap) {
        this(toEntries(modelCaps), defaultCap);
    }

    /** Calcula las tres ventanas. Las listas contienen todas las variantes de cada ventana. */
    public Calculation calculate(List<OpenCodeModelStats> fiveHour,
                                 List<OpenCodeModelStats> sevenDay,
                                 List<OpenCodeModelStats> monthly) {
        Map<UsageWindowType, List<OpenCodeModelStats>> windows = new EnumMap<>(UsageWindowType.class);
        windows.put(UsageWindowType.FIVE_HOUR, safe(fiveHour));
        windows.put(UsageWindowType.SEVEN_DAY, safe(sevenDay));
        windows.put(UsageWindowType.MONTHLY, safe(monthly));
        return calculate(windows);
    }

    /** Calcula ventanas identificadas por {@link UsageWindowType}. */
    public Calculation calculate(Map<UsageWindowType, ? extends List<OpenCodeModelStats>> input) {
        Map<String, EnumMap<UsageWindowType, BigDecimal>> costs = new LinkedHashMap<>();
        for (UsageWindowType requestedWindow : List.of(UsageWindowType.FIVE_HOUR,
                UsageWindowType.SEVEN_DAY, UsageWindowType.MONTHLY,
                UsageWindowType.ROLLING, UsageWindowType.WEEKLY)) {
            UsageWindowType window = canonical(requestedWindow);
            List<OpenCodeModelStats> entries = input == null ? null : input.get(requestedWindow);
            if (entries == null) continue;
            for (OpenCodeModelStats entry : entries) {
                if (entry == null || entry.id() == null || entry.id().isBlank()) continue;
                BigDecimal cost = saneCost(entry.cost());
                EnumMap<UsageWindowType, BigDecimal> byWindow = costs.computeIfAbsent(
                        entry.id(), ignored -> new EnumMap<>(UsageWindowType.class));
                byWindow.merge(window, cost, BigDecimal::add);
            }
        }

        List<ModelUsage> models = new ArrayList<>();
        for (Map.Entry<String, EnumMap<UsageWindowType, BigDecimal>> entry : costs.entrySet()) {
            if (entry.getValue().values().stream().noneMatch(value -> value.signum() > 0)) continue;
            models.add(toUsage(entry.getKey(), entry.getValue()));
        }
        ModelUsage total = total(costs);
        return new Calculation(List.copyOf(models), total);
    }

    private ModelUsage toUsage(String id, Map<UsageWindowType, BigDecimal> costs) {
        BigDecimal cap = capFor(id);
        EnumMap<UsageWindowType, BigDecimal> percentages = new EnumMap<>(UsageWindowType.class);
        for (UsageWindowType window : List.of(UsageWindowType.FIVE_HOUR,
                UsageWindowType.SEVEN_DAY, UsageWindowType.MONTHLY)) {
            percentages.put(window, percentage(costs.getOrDefault(window, BigDecimal.ZERO), cap, window));
        }
        return new ModelUsage(id, cap, Map.copyOf(costs), Map.copyOf(percentages));
    }

    private ModelUsage total(Map<String, EnumMap<UsageWindowType, BigDecimal>> costs) {
        EnumMap<UsageWindowType, BigDecimal> totalCosts = new EnumMap<>(UsageWindowType.class);
        EnumMap<UsageWindowType, BigDecimal> percentages = new EnumMap<>(UsageWindowType.class);
        for (UsageWindowType window : List.of(UsageWindowType.FIVE_HOUR,
                UsageWindowType.SEVEN_DAY, UsageWindowType.MONTHLY)) {
            BigDecimal spent = BigDecimal.ZERO;
            BigDecimal denominator = BigDecimal.ZERO;
            for (Map<UsageWindowType, BigDecimal> model : costs.values()) {
                BigDecimal value = model.getOrDefault(window, BigDecimal.ZERO);
                if (value.signum() > 0) {
                    spent = spent.add(value);
                    denominator = denominator.add(windowCap(capFor(findModel(costs, model)), window));
                }
            }
            totalCosts.put(window, spent);
            percentages.put(window, denominator.signum() == 0 ? BigDecimal.ZERO
                    : saturate(spent.multiply(ONE_HUNDRED, CALCULATION_CONTEXT)
                    .divide(denominator, CALCULATION_CONTEXT)));
        }
        return new ModelUsage("Total", null, Map.copyOf(totalCosts), Map.copyOf(percentages));
    }

    private String findModel(Map<String, EnumMap<UsageWindowType, BigDecimal>> all,
                             Map<UsageWindowType, BigDecimal> value) {
        for (Map.Entry<String, EnumMap<UsageWindowType, BigDecimal>> entry : all.entrySet()) {
            if (entry.getValue() == value) return entry.getKey();
        }
        throw new IllegalStateException("Modelo no encontrado");
    }

    private BigDecimal percentage(BigDecimal cost, BigDecimal cap, UsageWindowType window) {
        return saturate(cost.multiply(ONE_HUNDRED, CALCULATION_CONTEXT)
                .divide(windowCap(cap, window), CALCULATION_CONTEXT));
    }

    private BigDecimal windowCap(BigDecimal cap, UsageWindowType window) {
        return switch (window) {
            case FIVE_HOUR -> cap.multiply(new BigDecimal("0.20"));
            case SEVEN_DAY -> cap.multiply(new BigDecimal("0.50"));
            case MONTHLY -> cap;
            default -> cap;
        };
    }

    private BigDecimal capFor(String model) { return modelCaps.getOrDefault(model, defaultCap); }
    private static UsageWindowType canonical(UsageWindowType window) {
        return switch (window) {
            case ROLLING -> UsageWindowType.FIVE_HOUR;
            case WEEKLY -> UsageWindowType.SEVEN_DAY;
            default -> window;
        };
    }
    private static BigDecimal saneCost(BigDecimal cost) { return positive(cost) ? cost : BigDecimal.ZERO; }
    private static boolean positive(BigDecimal value) { return value != null && value.signum() > 0; }
    private static BigDecimal saturate(BigDecimal value) { return value.compareTo(ONE_HUNDRED) > 0 ? ONE_HUNDRED : value; }
    private static <T> List<T> safe(List<T> value) { return value == null ? List.of() : value; }
    private static List<AppProperties.ModelCap> toEntries(Map<String, BigDecimal> caps) {
        List<AppProperties.ModelCap> result = new ArrayList<>();
        if (caps != null) caps.forEach((model, cap) -> result.add(new AppProperties.ModelCap(model, cap)));
        return result;
    }

    public record Calculation(List<ModelUsage> models, ModelUsage total) { }

    public record ModelUsage(String model, BigDecimal capUsd,
                             Map<UsageWindowType, BigDecimal> costs,
                             Map<UsageWindowType, BigDecimal> percentages) {
        public BigDecimal percentage(UsageWindowType window) {
            return percentages.getOrDefault(window, BigDecimal.ZERO);
        }
        public BigDecimal cost(UsageWindowType window) {
            return costs.getOrDefault(window, BigDecimal.ZERO);
        }
    }
}
