package io.cloudstub.core.download;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModuleDownloaderTest {

    private HttpServer server;
    private String baseUrl;
    private final Map<String, byte[]> files = new HashMap<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(
                "/",
                exchange -> {
                    byte[] body = files.get(exchange.getRequestURI().getPath());
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
        baseUrl = "http://localhost:" + server.getAddress().getPort() + "/maven2";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private String basePath(String version, String ext) {
        return "/maven2/io/github/cloudstub/cloudstub-sqs/"
                + version
                + "/cloudstub-sqs-"
                + version
                + "."
                + ext;
    }

    private void publish(String version, byte[] jar, String... checksumExts) {
        files.put(basePath(version, "jar"), jar);
        for (String ext : checksumExts) {
            files.put(
                    basePath(version, "jar." + ext),
                    checksum(jar, ext).getBytes(StandardCharsets.UTF_8));
        }
    }

    private void publishMetadata(String... versions) {
        StringBuilder xml = new StringBuilder("<metadata><versioning><versions>");
        for (String v : versions) {
            xml.append("<version>").append(v).append("</version>");
        }
        xml.append("</versions></versioning></metadata>");
        files.put(
                "/maven2/io/github/cloudstub/cloudstub-sqs/maven-metadata.xml",
                xml.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String checksum(byte[] data, String ext) {
        String algo =
                switch (ext) {
                    case "sha512" -> "SHA-512";
                    case "sha256" -> "SHA-256";
                    case "sha1" -> "SHA-1";
                    default -> throw new IllegalArgumentException(ext);
                };
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(algo).digest(data));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void downloadsAndVerifiesJar(@TempDir Path dir) throws IOException {
        byte[] jar = "fake-jar-bytes".getBytes(StandardCharsets.UTF_8);
        publish("0.1.0", jar, "sha512", "sha256", "sha1");

        Path written = new ModuleDownloader(baseUrl).download("sqs", "0.1.0", dir);

        assertEquals(dir.resolve("cloudstub-sqs-0.1.0.jar"), written);
        assertTrue(Files.exists(written));
        assertEquals("fake-jar-bytes", Files.readString(written));
    }

    @Test
    void verifiesWithSha1WhenStrongerChecksumsAbsent(@TempDir Path dir) {
        byte[] jar = "only-sha1".getBytes(StandardCharsets.UTF_8);
        publish("0.1.0", jar, "sha1");

        Path written = new ModuleDownloader(baseUrl).download("sqs", "0.1.0", dir);
        assertTrue(Files.exists(written));
    }

    @Test
    void rejectsChecksumMismatch(@TempDir Path dir) {
        byte[] jar = "good".getBytes(StandardCharsets.UTF_8);
        files.put(basePath("0.1.0", "jar"), jar);
        files.put(
                basePath("0.1.0", "jar.sha512"),
                checksum("tampered".getBytes(StandardCharsets.UTF_8), "sha512")
                        .getBytes(StandardCharsets.UTF_8));

        ModuleDownloader downloader = new ModuleDownloader(baseUrl);
        ModuleDownloadException ex =
                assertThrows(
                        ModuleDownloadException.class,
                        () -> downloader.download("sqs", "0.1.0", dir));
        assertTrue(ex.getMessage().contains("checksum mismatch"));
        assertFalse(Files.exists(dir.resolve("cloudstub-sqs-0.1.0.jar")));
    }

    @Test
    void rejectsWhenNoChecksumPublished(@TempDir Path dir) {
        files.put(basePath("0.1.0", "jar"), "no-checksum".getBytes(StandardCharsets.UTF_8));

        ModuleDownloader downloader = new ModuleDownloader(baseUrl);
        ModuleDownloadException ex =
                assertThrows(
                        ModuleDownloadException.class,
                        () -> downloader.download("sqs", "0.1.0", dir));
        assertTrue(ex.getMessage().contains("no checksum"));
        assertFalse(Files.exists(dir.resolve("cloudstub-sqs-0.1.0.jar")));
    }

    @Test
    void failsFastWithCoordinateWhenArtifactMissing(@TempDir Path dir) {
        ModuleDownloader downloader = new ModuleDownloader(baseUrl);
        ModuleDownloadException ex =
                assertThrows(
                        ModuleDownloadException.class,
                        () -> downloader.download("sqs", "9.9.9", dir));
        assertTrue(ex.getMessage().contains("io.github.cloudstub:cloudstub-sqs:9.9.9"));
        assertTrue(ex.getMessage().contains("sqs"));
        assertTrue(ex.getMessage().contains("Place the jar manually"));
        assertTrue(ex.getMessage().contains("--no-download"));
    }

    @Test
    void fallsBackToHighestPublishedAtMostRequested(@TempDir Path dir) throws IOException {
        byte[] beta4 = "beta4".getBytes(StandardCharsets.UTF_8);
        byte[] beta5 = "beta5".getBytes(StandardCharsets.UTF_8);
        publish("0.1.0-beta.4", beta4, "sha512");
        publish("0.1.0-beta.5", beta5, "sha512");
        publishMetadata("0.1.0-beta.4", "0.1.0-beta.5");

        Path written = new ModuleDownloader(baseUrl).download("sqs", "0.1.0-beta.6", dir);

        assertEquals(dir.resolve("cloudstub-sqs-0.1.0-beta.5.jar"), written);
        assertEquals("beta5", Files.readString(written));
    }

    @Test
    void fallbackExcludesVersionsNewerThanRequested(@TempDir Path dir) throws IOException {
        byte[] beta5 = "beta5".getBytes(StandardCharsets.UTF_8);
        byte[] beta7 = "beta7".getBytes(StandardCharsets.UTF_8);
        publish("0.1.0-beta.5", beta5, "sha512");
        publish("0.1.0-beta.7", beta7, "sha512");
        publishMetadata("0.1.0-beta.5", "0.1.0-beta.7");

        Path written = new ModuleDownloader(baseUrl).download("sqs", "0.1.0-beta.6", dir);

        assertEquals(dir.resolve("cloudstub-sqs-0.1.0-beta.5.jar"), written);
        assertEquals("beta5", Files.readString(written));
    }

    @Test
    void reusesCachedFallbackJarWithoutRedownloading(@TempDir Path dir) throws IOException {
        // The resolved (beta.5) jar is already cached but its bytes/checksums are NOT served, so a
        // re-download would fail; the cached jar must be returned from metadata resolution alone.
        Files.writeString(dir.resolve("cloudstub-sqs-0.1.0-beta.5.jar"), "cached-beta5");
        publishMetadata("0.1.0-beta.4", "0.1.0-beta.5");

        Path written = new ModuleDownloader(baseUrl).download("sqs", "0.1.0-beta.6", dir);

        assertEquals(dir.resolve("cloudstub-sqs-0.1.0-beta.5.jar"), written);
        assertEquals("cached-beta5", Files.readString(written));
    }

    @Test
    void failsWhenNoPublishedVersionIsAtMostRequested(@TempDir Path dir) {
        publish("0.1.0-beta.7", "beta7".getBytes(StandardCharsets.UTF_8), "sha512");
        publishMetadata("0.1.0-beta.7");

        ModuleDownloader downloader = new ModuleDownloader(baseUrl);
        ModuleDownloadException ex =
                assertThrows(
                        ModuleDownloadException.class,
                        () -> downloader.download("sqs", "0.1.0-beta.6", dir));
        assertTrue(ex.getMessage().contains("no earlier published version was found"));
        assertFalse(Files.exists(dir.resolve("cloudstub-sqs-0.1.0-beta.7.jar")));
    }

    @Test
    void rejectsPathTraversalInServiceOrVersion(@TempDir Path dir) {
        ModuleDownloader downloader = new ModuleDownloader(baseUrl);

        assertThrows(
                ModuleDownloadException.class, () -> downloader.download("../evil", "0.1.0", dir));
        assertThrows(
                ModuleDownloadException.class,
                () -> downloader.download("sqs", "../../etc/passwd", dir));
        assertThrows(
                ModuleDownloadException.class,
                () -> downloader.download("sqs/nested", "0.1.0", dir));

        assertEquals(0, dir.toFile().list().length);
    }

    @Test
    void isCachedDelegatesToTheModuleCache(@TempDir Path dir) throws IOException {
        assertFalse(ModuleDownloader.isCached(dir, "sqs", "0.1.0"));
        Files.createFile(dir.resolve("cloudstub-sqs-0.1.0.jar"));
        assertTrue(ModuleDownloader.isCached(dir, "sqs", "0.1.0"));
        assertFalse(ModuleDownloader.isCached(dir, "sqs", "0.2.0"));
    }

    @Test
    void downloadPrunesStaleCachedVersions(@TempDir Path dir) throws IOException {
        Files.createFile(dir.resolve("cloudstub-sqs-0.0.9.jar"));
        byte[] jar = "fresh".getBytes(StandardCharsets.UTF_8);
        publish("0.1.0", jar, "sha512");

        new ModuleDownloader(baseUrl).download("sqs", "0.1.0", dir);

        assertTrue(Files.exists(dir.resolve("cloudstub-sqs-0.1.0.jar")));
        assertFalse(Files.exists(dir.resolve("cloudstub-sqs-0.0.9.jar")));
    }
}
