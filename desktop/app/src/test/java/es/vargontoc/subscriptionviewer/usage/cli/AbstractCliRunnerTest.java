package es.vargontoc.subscriptionviewer.usage.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AbstractCliRunnerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void returnsOutputAndExitCode() throws IOException {
        assumeUnix();
        TestRunner runner = new TestRunner(script("printf hello; exit 7"), Duration.ofSeconds(2), 1024);

        AbstractCliRunner.CliExecution result = runner.run(List.of());

        assertThat(result.success()).isFalse();
        assertThat(result.output()).isEqualTo("hello");
        assertThat(result.exitCode()).isEqualTo(7);
    }

    @Test
    void stopsAtOutputLimit() throws IOException {
        assumeUnix();
        TestRunner runner = new TestRunner(script("printf 0123456789; while true; do :; done"),
                Duration.ofSeconds(2), 4);

        AbstractCliRunner.CliExecution result = runner.run(List.of());

        assertThat(result.success()).isFalse();
        assertThat(runner.count()).isZero();
    }

    @Test
    void destroysProcessOnTimeout() throws IOException {
        assumeUnix();
        TestRunner runner = new TestRunner(script("while true; do :; done"), Duration.ofMillis(100), 1024);

        AbstractCliRunner.CliExecution result = runner.run(List.of());

        assertThat(result.success()).isFalse();
        assertThat(runner.count()).isZero();
    }

    @Test
    void closesActiveProcesses() throws IOException {
        assumeUnix();
        TestRunner runner = new TestRunner(script("sleep 5"), Duration.ofSeconds(2), 1024);
        Thread execution = new Thread(() -> runner.run(List.of()));
        execution.start();
        while (runner.count() == 0) Thread.yield();

        runner.closeActive();
        assertThat(runner.count()).isZero();
        execution.interrupt();
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

    private final class TestRunner extends AbstractCliRunner {
        TestRunner(Path command, Duration timeout, long limit) {
            super(command.toString(), timeout, limit, "test-cli-stdout", "test");
        }

        CliExecution run(List<String> arguments) { return executeCli(arguments); }
        int count() { return activeProcessCount(); }
        void closeActive() { destroyActiveCliProcesses(); }
    }
}
