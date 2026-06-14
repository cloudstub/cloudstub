package io.cloudstub.local;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.jspecify.annotations.NonNull;

/**
 * Launches the local fat JAR as a subprocess and waits until it is accepting connections. Captures
 * the process stdout so tests can assert on startup log lines.
 */
final class LocalProcess implements AutoCloseable {

    private final Process process;
    private final List<String> output = new CopyOnWriteArrayList<>();

    private LocalProcess(Process process) {
        this.process = process;
    }

    static LocalProcess start(int port, String... extraArgs) throws Exception {
        String jarPath = System.getProperty("cloudstub.local.jar");
        assertNotNull(jarPath, "cloudstub.local.jar system property must be set");

        List<String> command =
                new ArrayList<>(
                        List.of(
                                "java",
                                "-jar",
                                jarPath,
                                "--port=" + port,
                                "--api-port=" + (port + 1000)));
        String modulesDir = System.getProperty("cloudstub.local.modules.dir");
        if (modulesDir != null) {
            command.add("--modules-dir=" + modulesDir);
        }

        return getLocalProcess(port, command, extraArgs);
    }

    /**
     * Starts the server with an explicit modules directory, overriding the system property. Useful
     * for tests that need a custom (e.g. filtered or empty) plugin directory.
     */
    static LocalProcess startWithModulesDir(int port, Path modulesDir, String... extraArgs)
            throws Exception {
        return startWithEnv(port, modulesDir, java.util.Map.of(), extraArgs);
    }

    /**
     * Starts the server with an explicit modules directory and extra environment variables. Used by
     * auto-download tests to point {@code CLOUDSTUB_MAVEN_BASE_URL} at a stub repository.
     */
    static LocalProcess startWithEnv(
            int port, Path modulesDir, java.util.Map<String, String> env, String... extraArgs)
            throws Exception {
        String jarPath = System.getProperty("cloudstub.local.jar");
        assertNotNull(jarPath, "cloudstub.local.jar system property must be set");

        List<String> command =
                new ArrayList<>(
                        List.of(
                                "java",
                                "-jar",
                                jarPath,
                                "--port=" + port,
                                "--api-port=" + (port + 1000),
                                "--modules-dir=" + modulesDir.toAbsolutePath()));
        command.addAll(List.of(extraArgs));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().putAll(env);
        pb.redirectErrorStream(true);
        LocalProcess sp = new LocalProcess(pb.start());
        sp.drainOutput();
        sp.awaitReady(port);
        return sp;
    }

    @NonNull
    private static LocalProcess getLocalProcess(int port, List<String> command, String[] extraArgs)
            throws IOException, InterruptedException {
        command.addAll(List.of(extraArgs));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        LocalProcess sp = new LocalProcess(pb.start());
        sp.drainOutput();
        sp.awaitReady(port);
        return sp;
    }

    /** Startup log lines captured from the subprocess stdout. */
    List<String> output() {
        return output;
    }

    /**
     * Waits up to {@code timeoutMs} for a captured stdout line to match {@code predicate}.
     * Necessary because stdout is drained asynchronously, so a line may not be in {@link #output()}
     * the instant the server starts accepting connections.
     *
     * @return {@code true} if a matching line appeared before the timeout
     */
    boolean awaitOutput(Predicate<String> predicate) throws InterruptedException {
        long deadline = System.currentTimeMillis() + (long) 5000;
        while (System.currentTimeMillis() < deadline) {
            if (output.stream().anyMatch(predicate)) {
                return true;
            }
            Thread.sleep(50);
        }
        return output.stream().anyMatch(predicate);
    }

    private void drainOutput() {
        Thread drainer =
                new Thread(
                        () -> {
                            try (BufferedReader r =
                                    new BufferedReader(
                                            new InputStreamReader(process.getInputStream()))) {
                                r.lines()
                                        .forEach(
                                                line -> {
                                                    output.add(line);
                                                    System.out.println("[local] " + line);
                                                });
                            } catch (IOException ignored) {
                            }
                        },
                        "local-output-drainer");
        drainer.setDaemon(true);
        drainer.start();
    }

    private void awaitReady(int port) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive()) {
                throw new IllegalStateException(
                        "Local process exited unexpectedly (exit=" + process.exitValue() + ")");
            }
            try (Socket s = new Socket("localhost", port)) {
                return; // connected — server is ready
            } catch (IOException e) {
                Thread.sleep(200);
            }
        }
        throw new IllegalStateException(
                "Local server did not start on port " + port + " within 30 s");
    }

    @Override
    public void close() throws InterruptedException {
        process.destroy();
        process.waitFor(5, TimeUnit.SECONDS);
    }
}
