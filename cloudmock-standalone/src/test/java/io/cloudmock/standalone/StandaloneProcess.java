package io.cloudmock.standalone;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Launches the standalone fat JAR as a subprocess and waits until it is accepting
 * connections. Captures the process stdout so tests can assert on startup log lines.
 */
final class StandaloneProcess implements AutoCloseable {

    private final Process process;
    private final List<String> output = new CopyOnWriteArrayList<>();

    private StandaloneProcess(Process process) {
        this.process = process;
    }

    static StandaloneProcess start(int port, String... extraArgs) throws Exception {
        String jarPath = System.getProperty("cloudmock.standalone.jar");
        assertNotNull(jarPath, "cloudmock.standalone.jar system property must be set");

        List<String> command = new ArrayList<>(List.of("java", "-jar", jarPath, "--port=" + port));
        command.addAll(List.of(extraArgs));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        StandaloneProcess sp = new StandaloneProcess(pb.start());
        sp.drainOutput();
        sp.awaitReady(port);
        return sp;
    }

    /** Startup log lines captured from the subprocess stdout. */
    List<String> output() {
        return output;
    }

    private void drainOutput() {
        Thread drainer = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                r.lines().forEach(line -> {
                    output.add(line);
                    System.out.println("[standalone] " + line);
                });
            } catch (IOException ignored) {
            }
        }, "standalone-output-drainer");
        drainer.setDaemon(true);
        drainer.start();
    }

    private void awaitReady(int port) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive()) {
                throw new IllegalStateException(
                        "Standalone process exited unexpectedly (exit=" + process.exitValue() + ")");
            }
            try (Socket s = new Socket("localhost", port)) {
                return; // connected — server is ready
            } catch (IOException e) {
                Thread.sleep(200);
            }
        }
        throw new IllegalStateException(
                "Standalone server did not start on port " + port + " within 30 s");
    }

    @Override
    public void close() throws InterruptedException {
        process.destroy();
        process.waitFor(5, TimeUnit.SECONDS);
    }
}
