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
    void passesCommandAndParametersAsSeparateArguments() throws IOException {
        assumeUnix();
        Path fixture = script("printf '%s\\n' \"$@\"");

        OpenCodeCliRunner.CliResult result = new OpenCodeCliRunner(fixture.toString(), Duration.ofSeconds(2), 4096)
                .run("2026-10-18T10:00:00Z", "2026-10-18T14:00:00Z");

        assertThat(result).extracting(OpenCodeCliRunner.CliResult::isSuccess).isEqualTo(true);
        assertThat(result.text()).containsSubsequence("api", "experimental.session.stats", "--param",
                "from=2026-10-18T10:00:00Z", "--param", "to=2026-10-18T14:00:00Z", "--param", "tools=none");
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
    void windowsCmdWrapperPreservesIso8601Arguments() throws IOException {
        assumeWindows();
        Path fixture = copyWindowsFixture("opencode-fixture.cmd");

        OpenCodeCliRunner.CliResult result = new OpenCodeCliRunner(fixture.toString(), Duration.ofSeconds(2), 4096)
                .run("2026-10-18T10:00:00+02:00", "2026-10-18T14:00:00Z");

        assertThat(result).extracting(OpenCodeCliRunner.CliResult::isSuccess).isEqualTo(true);
        assertThat(result.output()).contains("from=2026-10-18T10:00:00+02:00")
                .contains("to=2026-10-18T14:00:00Z")
                .contains("tools=none");
    }

    @Test
    void windowsCmdWrapperExitCodeIsAQuietFailure() throws IOException {
        assumeWindows();
        Path fixture = copyWindowsFixture("opencode-failure.cmd");

        OpenCodeCliRunner.CliResult result = new OpenCodeCliRunner(fixture.toString(), Duration.ofSeconds(2), 4096)
                .run("2026-10-18T10:00:00Z", "2026-10-18T14:00:00Z");

        assertThat(result.isFailure()).isTrue();
        assertThat(result.output()).isEmpty();
    }

    @Test
    void missingWindowsCmdWrapperIsAQuietFailure() throws IOException {
        assumeWindows();
        Path missing = temporaryDirectory.resolve("missing-opencode.cmd");

        OpenCodeCliRunner.CliResult result = new OpenCodeCliRunner(missing.toString(), Duration.ofSeconds(1), 4096)
                .run("2026-10-18T10:00:00Z", "2026-10-18T14:00:00Z");

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
