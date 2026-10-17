package es.vargontoc.subscriptionviewer.launcher;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class BackgroundLauncherTest {
    @Test
    void launcherReturnsPromptImmediatelyAndLeavesChildAlive() throws Exception {
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            String script = Files.readString(Path.of("scripts", "start-background.cmd"));
            assertThat(script).containsIgnoringCase("start").contains("/b");
            return;
        }
        Path dir = Files.createTempDirectory("background-launcher");
        Path jar = Files.createFile(dir.resolve("app-test.jar"));
        Path marker = dir.resolve("started");
        Path java = fakeJava(dir, marker);
        Instant started = Instant.now();
        Process process = launch(java, jar, dir);
        assertThat(process.waitFor(3, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThat(Duration.between(started, Instant.now()).toMillis()).isLessThan(3000);
        assertThat(Files.exists(marker)).isTrue();
    }

    @Test
    void missingJavaReturnsClearError() throws Exception {
        Path dir = Files.createTempDirectory("background-launcher-error");
        Path jar = Files.createFile(dir.resolve("app-test.jar"));
        Process process = launch(dir.resolve("missing-java"), jar, dir);
        assertThat(process.waitFor(3, TimeUnit.SECONDS)).isTrue();
        String output = new String(process.getInputStream().readAllBytes());
        output += new String(process.getErrorStream().readAllBytes());
        assertThat(process.exitValue()).isNotZero();
        assertThat(output).containsIgnoringCase("java");
    }

    private Process launch(Path javaExecutable, Path jar, Path work) throws IOException {
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        Path script = Path.of("scripts", windows ? "start-background.cmd" : "start-background.sh");
        var command = windows ? List.of("cmd", "/c", script.toString())
                : List.of("bash", script.toString());
        ProcessBuilder builder = new ProcessBuilder(command).directory(Path.of(".").toFile());
        Map<String, String> env = builder.environment();
        env.put("JAVA_CMD", javaExecutable.toString());
        env.put("APP_JAR", jar.toString());
        env.put("BACKGROUND_LOG", work.resolve("app.log").toString());
        builder.redirectErrorStream(true);
        return builder.start();
    }

    private Path fakeJava(Path dir, Path marker) throws IOException {
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        Path script = dir.resolve(windows ? "java.cmd" : "java");
        String content = windows
                ? "@echo off\nif \"%1\"==\"-version\" (echo java version \"21.0.1\" & exit /b 0)\necho started>\"" + marker + "\"\nping 127.0.0.1 -n 20 >nul\n"
                : "#!/bin/sh\nif [ \"$1\" = \"-version\" ]; then echo 'java version \"21.0.1\"'; exit 0; fi\ntouch '" + marker + "'\nsleep 20\n";
        Files.writeString(script, content);
        script.toFile().setExecutable(true);
        return script;
    }
}
