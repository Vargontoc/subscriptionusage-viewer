package es.vargontoc.subscriptionviewer.launcher;

import es.vargontoc.subscriptionviewer.usage.opencode.OpenCodeCliRunner;
import es.vargontoc.subscriptionviewer.usage.opencode.OpenCodeStatsParser;
import es.vargontoc.subscriptionviewer.usage.opencode.OpenCodeUsageSource;
import es.vargontoc.subscriptionviewer.usage.opencode.UsageWindowRanges;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Comprueba que las tres consultas lentas no ocupan el hilo de Swing. */
class OpenCodeResourceAndResponsivenessV2IntegrationTest {

    @Test
    void slowCliLeavesEdtResponsive() throws Exception {
        Path executable = slowFixture();
        OpenCodeCliRunner runner = new OpenCodeCliRunner(executable.toString(),
                Duration.ofSeconds(15), OpenCodeCliRunner.DEFAULT_MAX_OUTPUT_BYTES);
        OpenCodeUsageSource source = new OpenCodeUsageSource(runner,
                new OpenCodeStatsParser(new ObjectMapper()),
                new UsageWindowRanges(Clock.systemUTC(), ZoneId.of("UTC"), 1));
        try {
            CompletableFuture<?> pending = CompletableFuture.runAsync(source::readRaw);
            awaitProcess(source);
            long start = System.nanoTime();
            SwingUtilities.invokeAndWait(() -> { /* simula una acción de bandeja */ });
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            assertThat(elapsed).as("el EDT no debe esperar al CLI").isLessThan(1_000);
            assertThat(pending).isNotDone();
            pending.get(15, TimeUnit.SECONDS);
        } finally {
            source.close();
        }
    }

    private void awaitProcess(OpenCodeUsageSource source) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (source.liveProcessCount() == 0 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(source.liveProcessCount()).as("debe existir una consulta CLI en curso").isPositive();
    }

    private Path slowFixture() throws IOException {
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        Path directory = Files.createTempDirectory("slow-opencode");
        Path script = directory.resolve(windows ? "opencode.cmd" : "opencode");
        String content = windows
                ? "@echo off\r\nping 127.0.0.1 -n 7 >nul\necho {\"models\":[]}\r\n"
                : "#!/bin/sh\nsleep 6\nprintf '%s' '{\"models\":[]}'\n";
        Files.writeString(script, content);
        script.toFile().setExecutable(true);
        return script;
    }
}
