package es.vargontoc.subscriptionviewer.usage.opencode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/** Interpreta la salida JSON de {@code experimental.session.stats}. */
@Component
public final class OpenCodeStatsParser {
    private static final Logger log = LoggerFactory.getLogger(OpenCodeStatsParser.class);
    private static final List<String> TARGET_PROVIDERS = List.of("opencode-go", "opencode");
    private final ObjectMapper objectMapper;

    public OpenCodeStatsParser() {
        this(new ObjectMapper());
    }

    public OpenCodeStatsParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Devuelve un resultado válido, posiblemente vacío. Un JSON inválido o sin
     * la lista {@code models} se distingue de una lista sin modelos objetivo.
     */
    public ParseResult parse(String json) {
        if (json == null) {
            return ParseResult.failure();
        }
        try {
            Object decoded = objectMapper.readValue(json, Map.class);
            if (!(decoded instanceof Map<?, ?> root)
                    || !(root.get("data") instanceof Map<?, ?> data)
                    || !(data.get("models") instanceof List<?> entries)) {
                return ParseResult.failure();
            }

            List<OpenCodeModelStats> models = new ArrayList<>();
            for (Object entry : entries) {
                if (!(entry instanceof Map<?, ?> modelEntry)
                        || !(modelEntry.get("model") instanceof Map<?, ?> model)) {
                    return ParseResult.failure();
                }
                // El proveedor se filtra antes de interpretar el resto del objeto.
                // Así, campos de otros proveedores no afectan a esta consulta.
                if (!(model.get("providerID") instanceof String providerId)
                        || !TARGET_PROVIDERS.contains(providerId)) {
                    continue;
                }
                models.add(readModel(modelEntry, model));

            }
            return ParseResult.success(List.copyOf(models));
        } catch (Exception ignored) {
            // No se conserva el texto recibido: puede contener datos de sesión.
            return ParseResult.failure();
        }
    }

    public ParseResult parseStats(String json) {
        return parse(json);
    }

    private OpenCodeModelStats readModel(Map<?, ?> modelEntry, Map<?, ?> model) {
        String id = requiredString(model, "id");

        String providerId = requiredString(model, "providerID");
        String variant = model.get("variant") instanceof String text && !text.isBlank()
                ? text : "default";
        int steps = integer(modelEntry, "steps");
        if (!(modelEntry.get("tokens") instanceof Map<?, ?> tokenMap)) {
            throw new IllegalArgumentException("tokens missing");
        }
        OpenCodeTokens tokens = new OpenCodeTokens(
                nonNegativeLong(tokenMap, "input"),
                nonNegativeLong(tokenMap, "output"),
                nonNegativeLong(tokenMap, "reasoning"),
                cacheValue(tokenMap, "read"),
                cacheValue(tokenMap, "write"));
        BigDecimal cost = decimal(modelEntry, "cost");
        return new OpenCodeModelStats(id, providerId, variant, steps, tokens, cost);
    }

    private long cacheValue(Map<?, ?> tokens, String name) {
        if (!(tokens.get("cache") instanceof Map<?, ?> cache)) {
            throw new IllegalArgumentException("cache missing");
        }
        return nonNegativeLong(cache, name);
    }

    private String requiredString(Map<?, ?> values, String name) {
        Object value = values.get(name);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(name + " missing");
        }
        return text;
    }

    private int integer(Map<?, ?> values, String name) {
        long value = wholeNumber(values.get(name), name);
        if (value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(name + " out of range");
        }
        return (int) value;
    }

    private long nonNegativeLong(Map<?, ?> values, String name) {
        long value = wholeNumber(values.get(name), name);
        if (value < 0) {
            throw new IllegalArgumentException(name + " negative");
        }
        return value;
    }

    private long wholeNumber(Object value, String name) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(name + " missing");
        }
        BigDecimal decimal;
        try {
            decimal = new BigDecimal(number.toString());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " invalid", exception);
        }
        try {
            return decimal.toBigIntegerExact().longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(name + " invalid", exception);
        }
    }

    private BigDecimal decimal(Map<?, ?> values, String name) {
        Object value = values.get(name);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(name + " missing");
        }
        try {
            BigDecimal result = new BigDecimal(number.toString());
            if (result.signum() < 0) {
                throw new IllegalArgumentException(name + " negative");
            }
            return result;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " invalid", exception);
        }
    }

    public record ParseResult(boolean valid, List<OpenCodeModelStats> models) {
        public static ParseResult success(List<OpenCodeModelStats> models) {
            return new ParseResult(true, models);
        }

        public static ParseResult failure() {
            return new ParseResult(false, List.of());
        }

        public boolean isValid() {
            return valid;
        }
    }
}
