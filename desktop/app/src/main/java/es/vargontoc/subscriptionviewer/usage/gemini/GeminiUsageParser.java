package es.vargontoc.subscriptionviewer.usage.gemini;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import es.vargontoc.subscriptionviewer.usage.UsageWindowType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Valida el sobre JSON devuelto por {@code agy}. */
@Component
public final class GeminiUsageParser {

    private static final String NO_ES_JSON = "no-es-json";
    private static final String STATUS_NO_ES_SUCCESS = "status-no-es-success";
    private static final String GRUPOS_AUSENTES = "grupos-ausentes";

    private final ObjectMapper objectMapper;

    public GeminiUsageParser() {
        this(new ObjectMapper());
    }

    public GeminiUsageParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Valida el sobre y extrae los grupos válidos. Los datos que no reconoce el
     * parser se omiten para que un cubo defectuoso no invalide los demás.
     */
    public ParseResult parse(String output) {
        if (output == null) {
            return ParseResult.invalid(NO_ES_JSON);
        }

        final JsonNode root;
        try {
            root = objectMapper.readTree(output);
        } catch (JacksonException ignored) {
            // La salida puede contener datos sensibles y nunca se incluye en el motivo.
            return ParseResult.invalid(NO_ES_JSON);
        }

        if (root == null || !root.isObject()) {
            return ParseResult.invalid(NO_ES_JSON);
        }

        JsonNode status = root.get("status");
        if (status == null || !status.isTextual() || !"SUCCESS".equals(status.textValue())) {
            return ParseResult.invalid(STATUS_NO_ES_SUCCESS);
        }

        JsonNode command = root.get("command");
        JsonNode data = command == null || !command.isObject() ? null : command.get("data");
        JsonNode groups = data == null || !data.isObject() ? null : data.get("groups");
        if (groups == null || !groups.isArray()) {
            return ParseResult.invalid(GRUPOS_AUSENTES);
        }

        List<GeminiGroup> parsedGroups = parseGroups(groups);
        if (parsedGroups.isEmpty()) {
            return ParseResult.invalid("sin-grupos-validos");
        }
        return ParseResult.valid(parsedGroups);
    }

    private List<GeminiGroup> parseGroups(JsonNode groups) {
        List<GeminiGroup> result = new ArrayList<>();
        for (JsonNode group : groups) {
            if (group == null || !group.isObject()) {
                continue;
            }
            JsonNode nameNode = group.get("name");
            JsonNode buckets = group.get("buckets");
            if (nameNode == null || !nameNode.isTextual() || buckets == null || !buckets.isArray()) {
                continue;
            }

            List<GeminiWindow> windows = new ArrayList<>();
            for (JsonNode bucket : buckets) {
                GeminiWindow window = parseWindow(bucket);
                if (window != null) {
                    windows.add(window);
                }
            }
            if (!windows.isEmpty()) {
                result.add(new GeminiGroup(nameNode.textValue(), windows));
            }
        }
        return List.copyOf(result);
    }

    private GeminiWindow parseWindow(JsonNode bucket) {
        if (bucket == null || !bucket.isObject()) {
            return null;
        }
        JsonNode windowNode = bucket.get("window");
        JsonNode fractionNode = bucket.get("remaining_fraction");
        if (windowNode == null || !windowNode.isTextual()
                || fractionNode == null || !fractionNode.isNumber()) {
            return null;
        }

        UsageWindowType type = switch (windowNode.textValue()) {
            case "weekly" -> UsageWindowType.WEEKLY;
            case "5h" -> UsageWindowType.FIVE_HOUR;
            default -> null;
        };
        if (type == null) {
            return null;
        }

        double fraction = fractionNode.doubleValue();
        if (!Double.isFinite(fraction)) {
            return null;
        }
        fraction = Math.max(0.0, Math.min(1.0, fraction));
        double percentage = BigDecimal.ONE.subtract(BigDecimal.valueOf(fraction))
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
        return new GeminiWindow(type, percentage, parseResetTime(bucket.get("reset_time")));
    }

    private Instant parseResetTime(JsonNode resetTime) {
        if (resetTime == null || !resetTime.isTextual()) {
            return null;
        }
        try {
            return Instant.parse(resetTime.textValue());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    public record ParseResult(boolean valid, List<GeminiGroup> groups, String invalidReason) {
        public static ParseResult valid(List<GeminiGroup> groups) {
            return new ParseResult(true, groups, null);
        }

        public static ParseResult invalid(String reason) {
            return new ParseResult(false, List.of(), reason);
        }

        public boolean isValid() {
            return valid;
        }

        public String reason() {
            return invalidReason;
        }
    }
}
