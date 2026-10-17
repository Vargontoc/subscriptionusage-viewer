package es.vargontoc.subscriptionviewer.usage.claude;

import tools.jackson.databind.ObjectMapper;
import es.vargontoc.subscriptionviewer.config.AppProperties;
import es.vargontoc.subscriptionviewer.config.ConfigurationProvider;
import es.vargontoc.subscriptionviewer.usage.UsageReading;
import es.vargontoc.subscriptionviewer.usage.UsageReadResult;
import es.vargontoc.subscriptionviewer.usage.UsageWindowType;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pruebas unitarias de {@link ClaudeUsageSource}.
 *
 * <p>Verifica la lectura y validación del fichero de estado, incluyendo
 * casos de éxito, JSON inválido, ventanas parciales y fallback de {@code written_at}.</p>
 */
class ClaudeUsageSourceTest {

    private static final Instant FILE_TIME = Instant.parse("2026-10-16T10:32:15Z");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Environment ENVIRONMENT = mock(Environment.class);

    @Test
    void shouldReturnReadingWithFortyAndFifteenPercent() {
        String json = """
                {
                  "written_at": 1792175400,
                  "five_hour": { "used_percentage": 40, "resets_at": 1792186200 },
                  "seven_day": { "used_percentage": 15, "resets_at": 1792544400 }
                }
                """;
        ClaudeUsageSource source = createSource(json, FILE_TIME);

        UsageReadResult result = source.read();

        assertThat(result.isPresent()).isTrue();
        UsageReading reading = result.get();
        assertThat(reading.providerId()).isEqualTo("claude");
        assertThat(reading.writtenAt()).isEqualTo(Instant.ofEpochSecond(1792175400));
        assertThat(reading.windows()).hasSize(2);
        assertThat(reading.windows().get(0).type()).isEqualTo(UsageWindowType.FIVE_HOUR);
        assertThat(reading.windows().get(0).usedPercentage()).isEqualTo(40);
        assertThat(reading.windows().get(0).resetsAt()).isEqualTo(Instant.ofEpochSecond(1792186200));
        assertThat(reading.windows().get(1).type()).isEqualTo(UsageWindowType.SEVEN_DAY);
        assertThat(reading.windows().get(1).usedPercentage()).isEqualTo(15);
        assertThat(reading.windows().get(1).resetsAt()).isEqualTo(Instant.ofEpochSecond(1792544400));
    }

    @Test
    void shouldPreserveIntegerValues() {
        String json = """
                {
                  "five_hour": { "used_percentage": 7, "resets_at": 1792186200 },
                  "seven_day": { "used_percentage": 11, "resets_at": 1792544400 }
                }
                """;
        ClaudeUsageSource source = createSource(json, FILE_TIME);

        UsageReadResult result = source.read();

        assertThat(result.isPresent()).isTrue();
        assertThat(result.get().windows().get(0).usedPercentage()).isEqualTo(7);
        assertThat(result.get().windows().get(1).usedPercentage()).isEqualTo(11);
    }

    @Test
    void shouldReturnEmptyWhenFileIsMissing() {
        ClaudeUsageSource source = createSource(new FixedStateFileLoader(Optional.empty()));

        UsageReadResult result = source.read();

        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void shouldReturnEmptyWhenJsonIsMalformed() {
        ClaudeUsageSource source = createSource("esto no es json", FILE_TIME);

        UsageReadResult result = source.read();

        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void shouldOmitIncompleteWindowAndKeepValidOne() {
        String json = """
                {
                  "five_hour": { "used_percentage": 40 },
                  "seven_day": { "used_percentage": 15, "resets_at": 1792544400 }
                }
                """;
        ClaudeUsageSource source = createSource(json, FILE_TIME);

        UsageReadResult result = source.read();

        assertThat(result.isPresent()).isTrue();
        assertThat(result.get().windows()).hasSize(1);
        assertThat(result.get().windows().get(0).type()).isEqualTo(UsageWindowType.SEVEN_DAY);
    }

    @Test
    void shouldOmitNonNumericOrOutOfRangePercentage() {
        String json = """
                {
                  "five_hour": { "used_percentage": "cuarenta", "resets_at": 1792186200 },
                  "seven_day": { "used_percentage": 150, "resets_at": 1792544400 }
                }
                """;
        ClaudeUsageSource source = createSource(json, FILE_TIME);

        UsageReadResult result = source.read();

        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void shouldUseFileTimestampWhenWrittenAtIsMissing() {
        String json = """
                {
                  "five_hour": { "used_percentage": 40, "resets_at": 1792186200 },
                  "seven_day": { "used_percentage": 15, "resets_at": 1792544400 }
                }
                """;
        ClaudeUsageSource source = createSource(json, FILE_TIME);

        UsageReadResult result = source.read();

        assertThat(result.isPresent()).isTrue();
        assertThat(result.get().writtenAt()).isEqualTo(FILE_TIME);
    }

    @Test
    void shouldUseFileTimestampWhenWrittenAtIsInvalid() {
        String json = """
                {
                  "written_at": "ayer",
                  "five_hour": { "used_percentage": 40, "resets_at": 1792186200 },
                  "seven_day": { "used_percentage": 15, "resets_at": 1792544400 }
                }
                """;
        ClaudeUsageSource source = createSource(json, FILE_TIME);

        UsageReadResult result = source.read();

        assertThat(result.isPresent()).isTrue();
        assertThat(result.get().writtenAt()).isEqualTo(FILE_TIME);
    }

    @Test
    void shouldReturnEmptyWhenBothWindowsAreInvalid() {
        String json = """
                {
                  "five_hour": { "used_percentage": -1, "resets_at": 1792186200 },
                  "seven_day": { "used_percentage": 101, "resets_at": 1792544400 }
                }
                """;
        ClaudeUsageSource source = createSource(json, FILE_TIME);

        UsageReadResult result = source.read();

        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void shouldIgnoreNonContractualFieldsAndCredentials() {
        String json = """
                {
                  "session_id": "9b69f8a3-9515-44d3-8c09-b6821b8ea37d",
                  "api_key": "sk-live-1234567890abcdef",
                  "token": "bearer-secret-token",
                  "password": "super-secret",
                  "rate_limits": {
                    "five_hour": { "used_percentage": 7, "resets_at": 1792186200 },
                    "seven_day": { "used_percentage": 11, "resets_at": 1792544400 }
                  },
                  "extra": { "nested": "value" }
                }
                """;
        ClaudeUsageSource source = createSource(json, FILE_TIME);

        UsageReadResult result = source.read();

        assertThat(result.isPresent()).isTrue();
        UsageReading reading = result.get();
        assertThat(reading.windows()).hasSize(2);
        assertThat(reading.windows().get(0).usedPercentage()).isEqualTo(7);
        assertThat(reading.windows().get(1).usedPercentage()).isEqualTo(11);
    }

    private ClaudeUsageSource createSource(String text, Instant writtenAt) {
        return createSource(new FixedStateFileLoader(Optional.of(new StateFileContent(text, writtenAt))));
    }

    private ClaudeUsageSource createSource(StateFileLoader loader) {
        AppProperties appProperties = new AppProperties();
        appProperties.setUsageStateFile("~/claude-status.json");
        ConfigurationProvider configurationProvider = new ConfigurationProvider(appProperties, ENVIRONMENT);
        return new ClaudeUsageSource(configurationProvider, loader, OBJECT_MAPPER);
    }

    private static class FixedStateFileLoader implements StateFileLoader {
        private final Optional<StateFileContent> content;

        FixedStateFileLoader(Optional<StateFileContent> content) {
            this.content = content;
        }

        @Override
        public Optional<StateFileContent> load(Path path) {
            return content;
        }
    }
}
