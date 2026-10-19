package es.vargontoc.subscriptionviewer.usage.openai;

import org.junit.jupiter.api.Test;
import es.vargontoc.subscriptionviewer.config.ConfigurationProvider;
import es.vargontoc.subscriptionviewer.usage.opencode.OpenCodeCliRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OpenAiCredentialsReaderTest {
    @Test
    void readsOnlyTheTwoRequiredCredentials() throws Exception {
        Path file = Files.createTempFile("codex-auth", ".json");
        Files.writeString(file, "{\"tokens\":{\"access_token\":\"token\",\"account_id\":\"account\",\"refresh_token\":\"refresh\",\"id_token\":\"id\"},\"email\":\"email\"}");
        OpenAiCredentialsReadResult result = new OpenAiCredentialsReader(file).read();
        assertThat(result.filePresent()).isTrue();
        assertThat(result.available()).isTrue();
        assertThat(result.credentials().orElseThrow().accessTokenForHeader()).isEqualTo("token");
        assertThat(result.credentials().orElseThrow().accountIdForHeader()).isEqualTo("account");
        assertThat(result.credentials().orElseThrow().toString()).doesNotContain("refresh", "email");
    }

    @Test
    void incompleteMissingInvalidAndUnchangedFilesAreHandledSafely() throws Exception {
        Path file = Files.createTempFile("codex-auth", ".json");
        String content = "{\"tokens\":{\"access_token\":\"token\"}}";
        Files.writeString(file, content);
        FileTime time = Files.getLastModifiedTime(file);
        OpenAiCredentialsReadResult incomplete = new OpenAiCredentialsReader(file).read();
        assertThat(incomplete.available()).isFalse();
        assertThat(Files.readString(file)).isEqualTo(content);
        assertThat(Files.getLastModifiedTime(file)).isEqualTo(time);

        Files.writeString(file, "not-json");
        assertThat(new OpenAiCredentialsReader(file).read().available()).isFalse();
        Path missing = file.resolveSibling("missing-auth.json");
        OpenAiCredentialsReadResult absent = new OpenAiCredentialsReader(missing).read();
        assertThat(absent.filePresent()).isFalse();
        assertThat(absent.available()).isFalse();
    }

    @Test
    void readsOpenCodeWhenCodexIsAbsentAndSectionIsConfigured() throws Exception {
        Path codex = Files.createTempDirectory("credentials").resolve("missing-auth.json");
        Path data = Files.createTempDirectory("opencode-data");
        Files.writeString(data.resolve("auth.json"),
                "{\"github\":{\"access\":\"ignored\"},\"openai\":{\"access\":\"oc-token\",\"accountId\":\"oc-account\"}}");
        ConfigurationProvider configuration = mock(ConfigurationProvider.class);
        OpenCodeCliRunner runner = mock(OpenCodeCliRunner.class);
        when(configuration.isOpenCodeConfigured()).thenReturn(true);
        when(runner.runDataPath()).thenReturn(new OpenCodeCliRunner.CliResult(true, data.toString()));

        OpenAiCredentialsReadResult result = new OpenAiCredentialsReader(codex, configuration, runner).read();

        assertThat(result.available()).isTrue();
        assertThat(result.credentials().orElseThrow().accessTokenForHeader()).isEqualTo("oc-token");
        assertThat(result.credentials().orElseThrow().accountIdForHeader()).isEqualTo("oc-account");
    }

    @Test
    void codexIsExclusiveEvenWhenOpenCodeHasDifferentCredentials() throws Exception {
        Path codex = Files.createTempFile("codex-auth", ".json");
        Files.writeString(codex, "{\"tokens\":{\"access_token\":\"codex-token\",\"account_id\":\"codex-account\"}}");
        ConfigurationProvider configuration = mock(ConfigurationProvider.class);
        OpenCodeCliRunner runner = mock(OpenCodeCliRunner.class);

        OpenAiCredentialsReadResult result = new OpenAiCredentialsReader(codex, configuration, runner).read();

        assertThat(result.credentials().orElseThrow().accessTokenForHeader()).isEqualTo("codex-token");
        verifyNoInteractions(configuration, runner);
    }

    @Test
    void incompleteCodexDoesNotFallBackToOpenCode() throws Exception {
        Path codex = Files.createTempFile("codex-auth", ".json");
        Files.writeString(codex, "{\"tokens\":{\"access_token\":\"only-token\"}}");
        ConfigurationProvider configuration = mock(ConfigurationProvider.class);
        OpenCodeCliRunner runner = mock(OpenCodeCliRunner.class);

        assertThat(new OpenAiCredentialsReader(codex, configuration, runner).read().available()).isFalse();
        verifyNoInteractions(configuration, runner);
    }

    @Test
    void absentSectionDoesNotExecuteOpenCode() throws Exception {
        Path codex = Files.createTempDirectory("credentials").resolve("missing-auth.json");
        ConfigurationProvider configuration = mock(ConfigurationProvider.class);
        OpenCodeCliRunner runner = mock(OpenCodeCliRunner.class);
        when(configuration.isOpenCodeConfigured()).thenReturn(false);

        assertThat(new OpenAiCredentialsReader(codex, configuration, runner).read().available()).isFalse();
        verifyNoInteractions(runner);
    }

    @Test
    void cliFailureAndMissingOpenAiLabelAreUnavailable() throws Exception {
        Path codex = Files.createTempDirectory("credentials").resolve("missing-auth.json");
        Path data = Files.createTempDirectory("opencode-data");
        Files.writeString(data.resolve("auth.json"), "{\"github\":{\"access\":\"ignored\"}}");
        ConfigurationProvider configuration = mock(ConfigurationProvider.class);
        OpenCodeCliRunner runner = mock(OpenCodeCliRunner.class);
        when(configuration.isOpenCodeConfigured()).thenReturn(true);
        when(runner.runDataPath()).thenReturn(new OpenCodeCliRunner.CliResult(false, ""));
        assertThat(new OpenAiCredentialsReader(codex, configuration, runner).read().available()).isFalse();
        when(runner.runDataPath()).thenReturn(new OpenCodeCliRunner.CliResult(true, data.toString()));
        assertThat(new OpenAiCredentialsReader(codex, configuration, runner).read().available()).isFalse();
    }

    @Test
    void rereadsChangedTokenOnEachCycle() throws Exception {
        Path codex = Files.createTempFile("codex-auth", ".json");
        Files.writeString(codex, "{\"tokens\":{\"access_token\":\"first\",\"account_id\":\"account\"}}");
        OpenAiCredentialsReader reader = new OpenAiCredentialsReader(codex);
        assertThat(reader.read().credentials().orElseThrow().accessTokenForHeader()).isEqualTo("first");
        Files.writeString(codex, "{\"tokens\":{\"access_token\":\"second\",\"account_id\":\"account\"}}");
        assertThat(reader.read().credentials().orElseThrow().accessTokenForHeader()).isEqualTo("second");
    }
}
