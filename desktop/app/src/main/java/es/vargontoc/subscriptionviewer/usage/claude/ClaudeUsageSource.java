package es.vargontoc.subscriptionviewer.usage.claude;

import tools.jackson.databind.ObjectMapper;
import es.vargontoc.subscriptionviewer.config.ConfigurationProvider;
import es.vargontoc.subscriptionviewer.usage.UsageReadResult;
import es.vargontoc.subscriptionviewer.usage.UsageReading;
import es.vargontoc.subscriptionviewer.usage.UsageSource;
import es.vargontoc.subscriptionviewer.usage.UsageWindowReading;
import es.vargontoc.subscriptionviewer.usage.UsageWindowType;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Fuente de uso para Claude que lee y valida el fichero de estado JSON.
 *
 * <p>El fichero puede contener un campo {@code written_at} en epoch de segundos;
 * si no está presente o no es válido, se usa el timestamp de modificación del
 * fichero. Cualquier error de lectura, JSON inválido o ventana no utilizable se
 * traduce en ausencia de datos sin propagar excepciones.</p>
 */
@Component
public class ClaudeUsageSource implements UsageSource {

    private static final String PROVIDER_ID = "claude";

    private final ConfigurationProvider configurationProvider;
    private final StateFileLoader stateFileLoader;
    private final ObjectMapper objectMapper;

    public ClaudeUsageSource(ConfigurationProvider configurationProvider,
                             StateFileLoader stateFileLoader,
                             ObjectMapper objectMapper) {
        this.configurationProvider = configurationProvider;
        this.stateFileLoader = stateFileLoader;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    /**
     * Realiza la lectura usando el resultado clasificado común.  Los errores
     * de lectura o de parseo se mantienen encapsulados y no alcanzan al
     * consumidor de la fuente.
     */
    @Override
    public UsageReadResult read() {
        try {
            return doRead().map(UsageReadResult::datos)
                    .orElseGet(() -> UsageReadResult.falloTransitorio(null));
        } catch (Exception e) {
            return UsageReadResult.falloTransitorio(e);
        }
    }

    @Override
    public Optional<UsageReading> doRead() throws Exception {
        Optional<StateFileContent> loaded = stateFileLoader.load(configurationProvider.getUsageStateFile());
        if (loaded.isEmpty()) {
            return Optional.empty();
        }

        StateFileContent content = loaded.get();
        if (content.text() == null || content.text().isBlank()) {
            return Optional.empty();
        }
        ClaudeStateFileDto dto = objectMapper.readValue(content.text(), ClaudeStateFileDto.class);

        Instant writtenAt = resolveWrittenAt(dto, content.writtenAt());
        if (writtenAt == null) {
            return Optional.empty();
        }

        List<UsageWindowReading> windows = resolveWindows(dto);
        if (windows.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new UsageReading(PROVIDER_ID, writtenAt, windows));
    }

    private List<UsageWindowReading> resolveWindows(ClaudeStateFileDto dto) {
        List<UsageWindowReading> windows = new ArrayList<>();
        if (dto.hasRateLimits()) {
            UsageWindowReading fiveHour = toWindow(dto.rateLimits.fiveHour, UsageWindowType.FIVE_HOUR);
            UsageWindowReading sevenDay = toWindow(dto.rateLimits.sevenDay, UsageWindowType.SEVEN_DAY);
            if (fiveHour == null || sevenDay == null) {
                return List.of();
            }
            windows.add(fiveHour);
            windows.add(sevenDay);
        } else {
            UsageWindowReading fiveHour = toWindow(dto.fiveHour, UsageWindowType.FIVE_HOUR);
            UsageWindowReading sevenDay = toWindow(dto.sevenDay, UsageWindowType.SEVEN_DAY);
            if (fiveHour != null) {
                windows.add(fiveHour);
            }
            if (sevenDay != null) {
                windows.add(sevenDay);
            }
        }
        return windows;
    }

    private Instant resolveWrittenAt(ClaudeStateFileDto dto, Instant fallback) {
        if (dto.writtenAt instanceof Number number && number.longValue() > 0) {
            return Instant.ofEpochSecond(number.longValue());
        }
        return fallback;
    }

    private UsageWindowReading toWindow(ClaudeWindowDto windowDto, UsageWindowType type) {
        if (windowDto == null) {
            return null;
        }
        if (windowDto.usedPercentage == null || windowDto.resetsAt == null) {
            return null;
        }
        double percentage = windowDto.usedPercentage.doubleValue();
        if (percentage < 0 || percentage > 100) {
            return null;
        }
        if (windowDto.resetsAt <= 0) {
            return null;
        }
        return new UsageWindowReading(type, (double) Math.round(percentage), Instant.ofEpochSecond(windowDto.resetsAt));
    }
}
