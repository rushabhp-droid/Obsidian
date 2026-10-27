package com.obsidian.codeguard;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs a scanner with ProcessBuilder and an argument list (never a shell), in the given working
 * directory, with a timeout. Output goes to files in that directory, not to logs.
 */
public class ToolRunner {

    public record Result(boolean available, int exitCode, String stdout) {}

    public Result run(Path workDir, Duration timeout, String program, String... args) {
        List<String> command = new ArrayList<>();
        command.add(program);
        command.addAll(List.of(args));
        Path stdout = workDir.resolve(Path.of(program).getFileName() + ".stdout");
        Path stderr = workDir.resolve(Path.of(program).getFileName() + ".stderr");
        try {
            Process process = new ProcessBuilder(command).directory(workDir.toFile())
                    .redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start();
            process.getOutputStream().close();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException(program + " didn't finish within " + timeout.toSeconds() + " s");
            }
            return new Result(true, process.exitValue(), Files.readString(stdout));
        } catch (IOException e) {
            return new Result(false, -1, "");   // not installed / not executable
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
