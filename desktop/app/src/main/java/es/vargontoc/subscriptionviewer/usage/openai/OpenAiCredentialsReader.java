package es.vargontoc.subscriptionviewer.usage.openai;

import org.springframework.stereotype.Component;
import es.vargontoc.subscriptionviewer.config.ConfigurationProvider;
import es.vargontoc.subscriptionviewer.usage.opencode.OpenCodeCliRunner;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Lee únicamente las credenciales necesarias del fichero de autenticación de Codex. */
@Component
public final class OpenAiCredentialsReader {
    private final Path authFile;
    private final ObjectMapper objectMapper;
    private final ConfigurationProvider configurationProvider;
    private final OpenCodeCliRunner openCodeCliRunner;

    @org.springframework.beans.factory.annotation.Autowired
    public OpenAiCredentialsReader(ConfigurationProvider configurationProvider,
            OpenCodeCliRunner openCodeCliRunner) {
        this(Path.of(System.getProperty("user.home"), ".codex", "auth.json"),
                new ObjectMapper(), configurationProvider, openCodeCliRunner);
    }

    /** Constructor con ruta inyectable para pruebas y para no depender del entorno. */
    public OpenAiCredentialsReader(Path authFile) {
        this(authFile, new ObjectMapper(), null, null);
    }

    OpenAiCredentialsReader(Path authFile, ObjectMapper objectMapper) {
        this(authFile, objectMapper, null, null);
    }

    /** Constructor con dependencias sustituibles para probar la prioridad de fuentes. */
    public OpenAiCredentialsReader(Path authFile, ConfigurationProvider configurationProvider,
            OpenCodeCliRunner openCodeCliRunner) {
        this(authFile, new ObjectMapper(), configurationProvider, openCodeCliRunner);
    }

    OpenAiCredentialsReader(Path authFile, ObjectMapper objectMapper,
            ConfigurationProvider configurationProvider, OpenCodeCliRunner openCodeCliRunner) {
        this.authFile = Objects.requireNonNull(authFile, "authFile");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.configurationProvider = configurationProvider;
        this.openCodeCliRunner = openCodeCliRunner;
    }

    /** Lee el fichero en modo lectura; los campos desconocidos se saltan durante el parseo. */
    public OpenAiCredentialsReadResult read() {
        // La mera existencia de Codex bloquea deliberadamente cualquier alternativa.
        if (Files.exists(authFile)) {
            return readJson(authFile, false);
        }

        if (configurationProvider == null || openCodeCliRunner == null
                || !configurationProvider.isOpenCodeConfigured()) {
            return new OpenAiCredentialsReadResult(false, null);
        }
        try {
            OpenCodeCliRunner.CliResult pathResult = openCodeCliRunner.runDataPath();
            if (pathResult == null || pathResult.isFailure() || pathResult.output().isBlank()) {
                return new OpenAiCredentialsReadResult(false, null);
            }
            return readJson(Path.of(pathResult.output().trim()).resolve("auth.json"), true);
        } catch (RuntimeException ignored) {
            return new OpenAiCredentialsReadResult(false, null);
        }
    }

    private OpenAiCredentialsReadResult readJson(Path file, boolean openCode) {
        if (!Files.isRegularFile(file)) {
            return new OpenAiCredentialsReadResult(openCode || Files.exists(file), null);
        }
        try (InputStream input = Files.newInputStream(file);
             JsonParser parser = objectMapper.createParser(input)) {
            return parse(parser, openCode);
        } catch (IOException | RuntimeException ignored) {
            return new OpenAiCredentialsReadResult(true, null);
        }
    }

    private OpenAiCredentialsReadResult parse(JsonParser parser, boolean openCode) throws IOException {
            String accessToken = null;
            String accountId = null;
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                return new OpenAiCredentialsReadResult(true, null);
            }
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                if (parser.currentToken() != JsonToken.PROPERTY_NAME) {
                    return new OpenAiCredentialsReadResult(true, null);
                }
                String name = parser.currentName();
                JsonToken valueToken = parser.nextToken();
                if ((openCode ? "openai" : "tokens").equals(name) && valueToken == JsonToken.START_OBJECT) {
                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        if (parser.currentToken() != JsonToken.PROPERTY_NAME) {
                            return new OpenAiCredentialsReadResult(true, null);
                        }
                        String tokenName = parser.currentName();
                        JsonToken tokenValue = parser.nextToken();
                        if ((openCode ? "access" : "access_token").equals(tokenName)
                                && tokenValue == JsonToken.VALUE_STRING) {
                            accessToken = parser.getString();
                        } else if ((openCode ? "accountId" : "account_id").equals(tokenName)
                                && tokenValue == JsonToken.VALUE_STRING) {
                            accountId = parser.getString();
                        } else {
                            parser.skipChildren();
                        }
                    }
                } else {
                    parser.skipChildren();
                }
            }
            // Obliga a consumir el documento completo: unas credenciales válidas
            // seguidas de JSON basura también son un documento inválido.
            if (parser.nextToken() != null) {
                return new OpenAiCredentialsReadResult(true, null);
            }
            if (accessToken == null || accessToken.isBlank() || accountId == null || accountId.isBlank()) {
                return new OpenAiCredentialsReadResult(true, null);
            }
            return new OpenAiCredentialsReadResult(true, new OpenAiCredentials(accessToken, accountId));
    }

    public OpenAiCredentialsReadResult readCredentials() {
        return read();
    }
}
