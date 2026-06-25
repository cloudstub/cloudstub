package io.cloudstub.local;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end test of service auto-download through the running launcher: a declared service whose
 * jar is absent is fetched (from a stub Maven repository), checksum-verified, written into the
 * plugin directory, and loaded.
 */
class LocalAutoDownloadTest {

    private static final int PORT = 14599;

    @Test
    void declaringAbsentServiceDownloadsAndLoadsIt(@TempDir Path emptyModules) throws Exception {
        // The real cloudstub-sqs jar built for the integration test, served from a stub repo.
        Path sqsJar = findModuleJar("cloudstub-sqs");
        String version = versionOf(sqsJar, "cloudstub-sqs");
        byte[] jarBytes = Files.readAllBytes(sqsJar);

        HttpServer repo = stubRepo(version, jarBytes);
        String baseUrl = "http://localhost:" + repo.getAddress().getPort() + "/maven2";

        try (LocalProcess server =
                LocalProcess.startWithEnv(
                        PORT,
                        emptyModules,
                        Map.of("CLOUDSTUB_MAVEN_BASE_URL", baseUrl),
                        "--services=sqs",
                        "--module-version=" + version)) {

            assertTrue(
                    server.awaitOutput(
                            line ->
                                    line.contains("Provisioned service 'sqs'")
                                            && line.contains("cloudstub-sqs-" + version + ".jar")),
                    "expected a provisioning log line; got: " + server.output());
            assertTrue(
                    server.awaitOutput(line -> line.contains("Enabled services: sqs")),
                    "sqs should be enabled after download; got: " + server.output());
            assertTrue(
                    Files.exists(emptyModules.resolve("cloudstub-sqs-" + version + ".jar")),
                    "downloaded jar should be cached in the plugin directory");
        } finally {
            repo.stop(0);
        }
    }

    @Test
    void disabledDownloadWithAbsentServiceFailsFast(@TempDir Path emptyModules) throws Exception {
        String jarPath = System.getProperty("cloudstub.local.jar");
        assertNotNull(jarPath, "cloudstub.local.jar system property must be set");

        Process process =
                new ProcessBuilder(
                                "java",
                                "-jar",
                                jarPath,
                                "--port=" + (PORT + 1),
                                "--api-port=" + (PORT + 1001),
                                "--modules-dir=" + emptyModules.toAbsolutePath(),
                                "--services=sqs",
                                "--no-download")
                        .redirectErrorStream(true)
                        .start();

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean exited = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(exited, "offline launch with a missing service should exit, not hang");
        assertTrue(process.exitValue() != 0, "should fail fast with a non-zero exit");
        assertTrue(
                output.contains("Unknown service(s): sqs")
                        && output.contains("Auto-download is disabled"),
                "expected an actionable offline message; got: " + output);
    }

    private static HttpServer stubRepo(String version, byte[] jarBytes) throws IOException {
        String dir = "/maven2/io/github/cloudstub/cloudstub-sqs/" + version + "/";
        String jarName = "cloudstub-sqs-" + version + ".jar";
        byte[] sha512 = sha512Hex(jarBytes).getBytes(StandardCharsets.UTF_8);

        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(
                "/",
                exchange -> {
                    String path = exchange.getRequestURI().getPath();
                    byte[] body = null;
                    if (path.equals(dir + jarName)) {
                        body = jarBytes;
                    } else if (path.equals(dir + jarName + ".sha512")) {
                        body = sha512;
                    }
                    if (body == null) {
                        exchange.sendResponseHeaders(404, -1);
                        exchange.close();
                        return;
                    }
                    exchange.sendResponseHeaders(200, body.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(body);
                    }
                });
        server.start();
        return server;
    }

    private static String sha512Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512").digest(data));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Path findModuleJar(String artifact) throws IOException {
        String modulesDir = System.getProperty("cloudstub.local.modules.dir");
        assertNotNull(modulesDir, "cloudstub.local.modules.dir system property must be set");
        try (Stream<Path> entries = Files.list(Path.of(modulesDir))) {
            List<Path> matches =
                    entries.filter(
                                    p ->
                                            p.getFileName().toString().startsWith(artifact + "-")
                                                    && p.getFileName().toString().endsWith(".jar"))
                            .toList();
            assertTrue(!matches.isEmpty(), "no " + artifact + " jar in " + modulesDir);
            return matches.get(0);
        }
    }

    private static String versionOf(Path jar, String artifact) {
        String name = jar.getFileName().toString();
        return name.substring((artifact + "-").length(), name.length() - ".jar".length());
    }
}
