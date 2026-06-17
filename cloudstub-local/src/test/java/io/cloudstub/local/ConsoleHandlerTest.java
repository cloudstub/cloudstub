package io.cloudstub.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link ConsoleHandler} end to end against a real {@code HttpServer}, using the
 * test-only site under {@code src/test/resources/test-site/}.
 */
class ConsoleHandlerTest {

    private static final HttpClient HTTP =
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    private static HttpServer server;
    private static int port;

    @BeforeAll
    static void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/console", new ConsoleHandler("/console", "test-site"));
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterAll
    static void stop() {
        server.stop(0);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return HTTP.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + path))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void availabilityReflectsWhetherIndexIsOnClasspath() {
        assertTrue(new ConsoleHandler("/console", "test-site").isAvailable());
        assertFalse(new ConsoleHandler("/console", "no-such-site").isAvailable());
    }

    @Test
    void servesIndexAtMountRoot() throws Exception {
        HttpResponse<String> r = get("/console/");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("TEST-CONSOLE-INDEX"));
        assertTrue(r.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));
    }

    @Test
    void redirectsMountPathWithoutTrailingSlash() throws Exception {
        HttpResponse<String> r = get("/console");
        assertEquals(302, r.statusCode());
        assertEquals("/console/", r.headers().firstValue("Location").orElse(null));
    }

    @Test
    void servesStaticAssetWithContentType() throws Exception {
        HttpResponse<String> r = get("/console/main.js");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("test-console-main"));
        assertEquals("text/javascript", r.headers().firstValue("Content-Type").orElse(null));
    }

    @Test
    void fallsBackToIndexForClientSideRoute() throws Exception {
        // An extensionless path with no matching asset is an Angular route → serve index.html.
        HttpResponse<String> r = get("/console/dashboard/queues");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("TEST-CONSOLE-INDEX"));
    }

    @Test
    void returns404ForMissingAsset() throws Exception {
        // A path that looks like a file (has an extension) must not fall back to index.html.
        HttpResponse<String> r = get("/console/missing.js");
        assertEquals(404, r.statusCode());
    }

    @Test
    void rejectsPathTraversal() throws Exception {
        HttpResponse<String> r = get("/console/../secret");
        assertEquals(404, r.statusCode());
    }
}
