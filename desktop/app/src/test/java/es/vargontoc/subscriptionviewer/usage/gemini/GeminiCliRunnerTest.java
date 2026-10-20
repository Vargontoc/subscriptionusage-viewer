package es.vargontoc.subscriptionviewer.usage.gemini;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeminiCliRunnerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void runsAgyWithTheFixedArguments() throws IOException {
        Path fixture = script("printf '%s\\n' \"$@\"");

        GeminiCliRunner.CliResult result = new GeminiCliRunner(fixture.toString(), Duration.ofSeconds(2), 4096).run();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).containsSubsequence("-p", "/usage", "--output-format", "json");
    }

    @Test
    void missingExecutableIsAQuietFailure() {
        GeminiCliRunner.CliResult result = new GeminiCliRunner(
                "definitely-not-an-agy-executable", Duration.ofSeconds(1), 4096).run();

        assertThat(result.isFailure()).isTrue();
        assertThat(result.output()).isEmpty();
    }

    @Test
    void nonZeroExitIsAQuietFailure() throws IOException {
        Path fixture = script("exit 7");

        GeminiCliRunner.CliResult result = new GeminiCliRunner(fixture.toString(), Duration.ofSeconds(2), 4096).run();

        assertThat(result.isFailure()).isTrue();
        assertThat(result.output()).isEmpty();
    }

    @Test
    void timeoutDestroysTheActiveProcess() throws IOException {
        Path fixture = script("while true; do :; done");
        GeminiCliRunner runner = new GeminiCliRunner(fixture.toString(), Duration.ofMillis(100), 4096);

        GeminiCliRunner.CliResult result = runner.run();

        assertThat(result.isFailure()).isTrue();
        assertThat(result.output()).isEmpty();
        assertThat(runner.liveProcessCount()).isZero();
    }

    @Test
    void outputAboveLimitIsAQuietFailure() throws IOException {
        Path fixture = script("dd if=/dev/zero bs=1024 count=1025 2>/dev/null");

        GeminiCliRunner.CliResult result = new GeminiCliRunner(
                fixture.toString(), Duration.ofSeconds(5), GeminiCliRunner.DEFAULT_MAX_OUTPUT_BYTES).run();

        assertThat(result.isFailure()).isTrue();
        assertThat(result.output()).isEmpty();
    }

    private Path script(String body) throws IOException {
        if (isWindows()) {
            Path file = temporaryDirectory.resolve("agy-fixture.cmd");
            Files.writeString(file, "@echo off\r\n" + windowsBody(body) + "\r\n", StandardCharsets.UTF_8);
            return file;
        }
        Path file = temporaryDirectory.resolve("agy-fixture.sh");
        Files.writeString(file, "#!/bin/sh\n" + body + "\n", StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(file, Set.of(PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
        return file;
    }

    private String windowsBody(String body) {
        if (body.startsWith("exit ")) return body;
        if (body.contains("while true")) return "ping 127.0.0.1 -n 30 > nul";
        if (body.startsWith("dd ")) return "for /L %%i in (1,1,200000) do @echo 0123456789";
        return "echo %1\necho %2\necho %3\necho %4";
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
