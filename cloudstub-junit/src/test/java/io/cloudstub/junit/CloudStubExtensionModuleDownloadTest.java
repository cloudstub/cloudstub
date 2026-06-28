package io.cloudstub.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import io.cloudstub.core.spi.CloudStubContext;
import io.cloudstub.core.spi.CloudStubService;
import io.cloudstub.core.spi.HttpMethod;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies {@link CloudStubExtension#withModules} downloads a published module jar from a Maven
 * repository, loads it via a classloader over the cache directory, and registers it without the
 * module being on the test classpath.
 *
 * <p>The served jar carries its own {@code META-INF/services} entry and class bytes, so the service
 * is discoverable only through the download, never the parent classpath.
 */
class CloudStubExtensionModuleDownloadTest {

    private static final String SERVICE_ID = "downloadtest";
    private static final String VERSION = "0.1.0";

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

    @Test
    void downloadsAndRegistersModuleNotOnClasspath(@TempDir Path cacheDir) throws Exception {
        byte[] jar = buildModuleJar();
        String base =
                "/maven2/io/github/cloudstub/cloudstub-"
                        + SERVICE_ID
                        + "/"
                        + VERSION
                        + "/cloudstub-"
                        + SERVICE_ID
                        + "-"
                        + VERSION;
        files.put(base + ".jar", jar);
        files.put(base + ".jar.sha512", sha512(jar).getBytes(StandardCharsets.UTF_8));

        CloudStubExtension cloudMock =
                new CloudStubExtension()
                        .withMavenBaseUrl(baseUrl)
                        .withModulesCacheDir(cacheDir)
                        .withModuleVersion(VERSION)
                        // withModule + withModules name the same id: collapses to one fetch.
                        .withModule(SERVICE_ID)
                        .withModules(SERVICE_ID);

        cloudMock.beforeAll(null);
        try {
            HttpResponse<String> response =
                    HttpClient.newHttpClient()
                            .send(
                                    HttpRequest.newBuilder(
                                                    URI.create(
                                                            "http://localhost:"
                                                                    + cloudMock.port()
                                                                    + "/ping"))
                                            .GET()
                                            .build(),
                                    HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertEquals("pong", response.body());
            assertTrue(
                    java.nio.file.Files.exists(
                            cacheDir.resolve("cloudstub-" + SERVICE_ID + "-" + VERSION + ".jar")),
                    "downloaded jar should be cached");
        } finally {
            cloudMock.afterAll(null);
        }
    }

    /**
     * Builds a jar containing {@link DownloadedTestService} (compiled into the test output) plus
     * the {@code META-INF/services} provider config, so {@link java.util.ServiceLoader} discovers
     * it only through the served jar.
     */
    private static byte[] buildModuleJar() throws IOException {
        String className = DownloadedTestService.class.getName();
        String classResource = className.replace('.', '/') + ".class";

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(out)) {
            jar.putNextEntry(new JarEntry("META-INF/services/" + CloudStubService.class.getName()));
            jar.write((className + "\n").getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();

            jar.putNextEntry(new JarEntry(classResource));
            try (InputStream in =
                    DownloadedTestService.class
                            .getClassLoader()
                            .getResourceAsStream(classResource)) {
                in.transferTo(jar);
            }
            jar.closeEntry();
        }
        return out.toByteArray();
    }

    private static String sha512(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512").digest(data));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Stub service registered by the downloaded module jar; serves {@code GET /ping}. */
    public static final class DownloadedTestService implements CloudStubService {

        @Override
        public String serviceId() {
            return SERVICE_ID;
        }

        @Override
        public void register(CloudStubContext context) {
            context.registrar().registerRestStub(HttpMethod.GET, "/ping", "pong");
        }
    }
}
