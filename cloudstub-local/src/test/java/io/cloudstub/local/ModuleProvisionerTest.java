package io.cloudstub.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises the auto-download fallback through {@link ModuleProvisioner}, the standalone consumer.
 */
class ModuleProvisionerTest {

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

    private void publishSqs(String version, String body) {
        byte[] jar = body.getBytes(StandardCharsets.UTF_8);
        String dir =
                "/maven2/io/github/cloudstub/cloudstub-sqs/"
                        + version
                        + "/cloudstub-sqs-"
                        + version;
        files.put(dir + ".jar", jar);
        files.put(dir + ".jar.sha512", sha512(jar).getBytes(StandardCharsets.UTF_8));
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

    private static String sha512(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512").digest(data));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void provisionsFallbackVersionWhenExactCoreVersionUnpublished(@TempDir Path dir)
            throws IOException {
        publishSqs("0.1.0-beta.5", "beta5-jar");
        publishMetadata("0.1.0-beta.4", "0.1.0-beta.5");

        ModuleProvisioner.provisionMissing(Set.of("sqs"), dir, "0.1.0-beta.6", baseUrl);

        Path jar = dir.resolve("cloudstub-sqs-0.1.0-beta.5.jar");
        assertTrue(Files.exists(jar));
        assertEquals("beta5-jar", Files.readString(jar));
    }

    @Test
    void usesCachedJarWhenProvisioningFails(@TempDir Path dir) throws IOException {
        // A jar is already cached, but neither the requested version nor metadata is available.
        Files.writeString(dir.resolve("cloudstub-sqs-0.1.0-beta.5.jar"), "cached-beta5");
        String emptyRepo = "http://localhost:" + server.getAddress().getPort() + "/empty-repo";

        // Must not call System.exit: the cached jar satisfies the request with a warning.
        ModuleProvisioner.provisionMissing(Set.of("sqs"), dir, "0.1.0-beta.6", emptyRepo);

        Path jar = dir.resolve("cloudstub-sqs-0.1.0-beta.5.jar");
        assertTrue(Files.exists(jar));
        assertEquals("cached-beta5", Files.readString(jar));
        assertFalse(Files.exists(dir.resolve("cloudstub-sqs-0.1.0-beta.6.jar")));
    }
}
