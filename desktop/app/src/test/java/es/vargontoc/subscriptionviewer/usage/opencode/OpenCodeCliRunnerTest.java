package es.vargontoc.subscriptionviewer.usage.opencode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class OpenCodeCliRunnerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void passesUnixMillisecondCommandParametersAsSeparateArguments() throws IOException {
        assumeUnix();
        Path fixture = script("printf '%s\\n' \"$@\"");

        OpenCodeCliRunner.CliResult result = new OpenCodeCliRunner(fixture.toString(), Duration.ofSeconds(2), 4096)
                .run("1792317600000", "1792332000000");

        assertThat(result).extracting(OpenCodeCliRunner.CliResult::isSuccess).isEqualTo(true);
        assertThat(result.text()).containsSubsequence("api", "experimental.session.stats", "--param",
                "from=1792317600000", "--param", "to=1792332000000", "--param", "tools=none");
    }

    @Test
    void missingExecutableIsAQuietFailure() {
        OpenCodeCliRunner.CliResult result = new OpenCodeCliRunner(
                "definitely-not-an-opencode-executable", Duration.ofSeconds(1), 4096)
                .run("from", "to");

        assertThat(result.isFailure()).isTrue();
        assertThat(result.text()).isEmpty();
    }

    @Test
    void outputAboveLimitIsAQuietFailure() throws IOException {
        assumeUnix();
        Path fixture = script("printf '0123456789'");

        OpenCodeCliRunner.CliResult result = new OpenCodeCliRunner(fixture.toString(), Duration.ofSeconds(2), 4)
                .run("from", "to");

        assertThat(result.isFailure()).isTrue();
        assertThat(result.text()).isEmpty();
    }

    @Test
    void dataPathCommandReturnsThePrintedPath() throws IOException {
        assumeUnix();
        Path fixture = script("printf '/tmp/opencode-data\\n'");

        OpenCodeCliRunner.CliResult result = new OpenCodeCliRunner(fixture.toString(), Duration.ofSeconds(2), 4096)
                .runDataPath();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).isEqualTo("/tmp/opencode-data");
    }

    @Test
    void dataPathCommandWithErrorIsAQuietFailure() throws IOException {
        assumeUnix();
        Path fixture = script("exit 7");

        OpenCodeCliRunner.CliResult result = new OpenCodeCliRunner(fixture.toString(), Duration.ofSeconds(2), 4096)
                .runDataPath();

        assertThat(result.isFailure()).isTrue();
        assertThat(result.output()).isEmpty();
    }

    @Test
    void dataPathCommandThatDoesNotFinishIsDestroyed() throws IOException {
        assumeUnix();
        Path fixture = script("while true; do :; done");
        OpenCodeCliRunner runner = new OpenCodeCliRunner(fixture.toString(), Duration.ofMillis(100), 4096);

        OpenCodeCliRunner.CliResult result = runner.runDataPath();

        assertThat(result.isFailure()).isTrue();
        assertThat(result.output()).isEmpty();
        assertThat(runner.liveProcessCount()).isZero();
    }

    @Test
    void emptyDataPathOutputIsUnavailable() throws IOException {
        assumeUnix();
        Path fixture = script("printf '   \\n'");

        OpenCodeCliRunner.CliResult result = new OpenCodeCliRunner(fixture.toString(), Duration.ofSeconds(2), 4096)
                .runDataPath();

        assertThat(result.isFailure()).isTrue();
        assertThat(result.output()).isEmpty();
    }

    @Test
    void windowsCmdWrapperPreservesUnixMillisecondArguments() throws IOException {
        assumeWindows();
        Path fixture = copyWindowsFixture("opencode-fixture.cmd");

        OpenCodeCliRunner.CliResult result = new OpenCodeCliRunner(fixture.toString(), Duration.ofSeconds(2), 4096)
                .run("1792317600000", "1792332000000");

        assertThat(result).extracting(OpenCodeCliRunner.CliResult::isSuccess).isEqualTo(true);
        assertThat(result.output()).contains("from=1792317600000")
                .contains("to=1792332000000")
                .contains("tools=none");
    }

    @Test
    void windowsCmdWrapperExitCodeIsAQuietFailure() throws IOException {
        assumeWindows();
        Path fixture = copyWindowsFixture("opencode-failure.cmd");

        OpenCodeCliRunner.CliResult result = new OpenCodeCliRunner(fixture.toString(), Duration.ofSeconds(2), 4096)
                .run("1792317600000", "1792332000000");

        assertThat(result.isFailure()).isTrue();
        assertThat(result.output()).isEmpty();
    }

    @Test
    void missingWindowsCmdWrapperIsAQuietFailure() throws IOException {
        assumeWindows();
        Path missing = temporaryDirectory.resolve("missing-opencode.cmd");

        OpenCodeCliRunner.CliResult result = new OpenCodeCliRunner(missing.toString(), Duration.ofSeconds(1), 4096)
                .run("1792317600000", "1792332000000");

        assertThat(result.isFailure()).isTrue();
        assertThat(result.output()).isEmpty();
    }

    private Path script(String body) throws IOException {
        Path file = temporaryDirectory.resolve("fixture.sh");
        Files.writeString(file, "#!/bin/sh\n" + body + "\n", StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(file, Set.of(PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
        return file;
    }

    private void assumeUnix() {
        assumeFalse(System.getProperty("os.name", "").toLowerCase().contains("win"));
    }

    private void assumeWindows() {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("win"));
    }

    private Path copyWindowsFixture(String name) throws IOException {
        Path fixture = temporaryDirectory.resolve(name);
        try (var input = getClass().getResourceAsStream("/opencode/" + name)) {
            if (input == null) throw new IOException("Fixture no encontrado: " + name);
            Files.copy(input, fixture);
        }
        return fixture;
    }
}
