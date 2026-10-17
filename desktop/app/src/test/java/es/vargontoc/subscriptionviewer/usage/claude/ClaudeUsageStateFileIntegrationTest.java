package es.vargontoc.subscriptionviewer.usage.claude;

import tools.jackson.databind.ObjectMapper;
import es.vargontoc.subscriptionviewer.config.AppProperties;
import es.vargontoc.subscriptionviewer.config.ConfigurationProvider;
import es.vargontoc.subscriptionviewer.usage.UsageReading;
import es.vargontoc.subscriptionviewer.usage.UsageWindowType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.Environment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pruebas de integración entre el fichero de estado producido por el hook y
 * {@link ClaudeUsageSource}.
 *
 * <p>NO ejecuta, modifica ni instrumenta el hook existente. Usa una copia de
 * un estado válido reproducido a partir de la salida observada del hook y
 * verifica que el lector interpreta correctamente los datos. También comprueba
 * que, al actualizar el fichero con contenido parcial o mezclado, el lector
 * devuelve ausencia en lugar de una lectura inconsistente.</p>
 */
class ClaudeUsageStateFileIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Environment ENVIRONMENT = mock(Environment.class);

    @Test
    void shouldReadValidHookStateFile(@TempDir Path tempDir) throws Exception {
        String json = """
                {
                  "session_id": "9b69f8a3-9515-44d3-8c09-b6821b8ea37d",
                  "rate_limits": {
                    "five_hour": { "used_percentage": 7, "resets_at": 1792186200 },
                    "seven_day": { "used_percentage": 11, "resets_at": 1792544400 }
                  }
                }
                """;
        Instant before = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Path stateFile = tempDir.resolve("statusline-dump.json");
        Files.writeString(stateFile, json);

        ClaudeUsageSource source = createSource(stateFile);
        Optional<UsageReading> result = source.read();
        Instant after = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        assertThat(result).isPresent();
        UsageReading reading = result.get();
        assertThat(reading.providerId()).isEqualTo("claude");
        assertThat(reading.writtenAt()).isAfterOrEqualTo(before).isBeforeOrEqualTo(after);
        assertThat(reading.windows()).hasSize(2);

        assertThat(reading.windows().get(0).type()).isEqualTo(UsageWindowType.FIVE_HOUR);
        assertThat(reading.windows().get(0).usedPercentage()).isEqualTo(7);
        assertThat(reading.windows().get(0).resetsAt()).isEqualTo(Instant.ofEpochSecond(1792186200));

        assertThat(reading.windows().get(1).type()).isEqualTo(UsageWindowType.SEVEN_DAY);
        assertThat(reading.windows().get(1).usedPercentage()).isEqualTo(11);
        assertThat(reading.windows().get(1).resetsAt()).isEqualTo(Instant.ofEpochSecond(1792544400));
    }

    @Test
    void shouldTolerateMissingRateLimitsAsAbsence(@TempDir Path tempDir) throws Exception {
        String validJson = """
                {
                  "rate_limits": {
                    "five_hour": { "used_percentage": 7, "resets_at": 1792186200 },
                    "seven_day": { "used_percentage": 11, "resets_at": 1792544400 }
                  }
                }
                """;
        Path stateFile = tempDir.resolve("statusline-dump.json");
        Files.writeString(stateFile, validJson);
        ClaudeUsageSource source = createSource(stateFile);

        assertThat(source.read()).isPresent();

        String partialJson = """
                {
                  "session_id": "9b69f8a3-9515-44d3-8c09-b6821b8ea37d"
                }
                """;
        Files.writeString(stateFile, partialJson);

        Optional<UsageReading> afterUpdate = source.read();
        assertThat(afterUpdate).isEmpty();
    }

    @Test
    void shouldTolerateMixedPartialDocumentAsAbsence(@TempDir Path tempDir) throws Exception {
        String validJson = """
                {
                  "rate_limits": {
                    "five_hour": { "used_percentage": 7, "resets_at": 1792186200 },
                    "seven_day": { "used_percentage": 11, "resets_at": 1792544400 }
                  }
                }
                """;
        Path stateFile = tempDir.resolve("statusline-dump.json");
        Files.writeString(stateFile, validJson);
        ClaudeUsageSource source = createSource(stateFile);

        assertThat(source.read()).isPresent();

        String mixedJson = """
                {
                  "rate_limits": {
                    "five_hour": { "used_percentage": 7 },
                    "seven_day": { "used_percentage": 11, "resets_at": 1792544400 }
                  }
                }
                """;
        Files.writeString(stateFile, mixedJson);

        Optional<UsageReading> afterUpdate = source.read();
        assertThat(afterUpdate).isEmpty();
    }

    private ClaudeUsageSource createSource(Path stateFile) {
        AppProperties appProperties = new AppProperties();
        appProperties.setUsageStateFile(stateFile.toString());
        ConfigurationProvider configurationProvider = new ConfigurationProvider(appProperties, ENVIRONMENT);
        return new ClaudeUsageSource(configurationProvider, new DefaultStateFileLoader(), OBJECT_MAPPER);
    }
}
