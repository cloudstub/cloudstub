package io.cloudmock.core;

import io.cloudmock.core.spi.CloudMockContext;
import io.cloudmock.core.spi.CloudMockService;
import io.cloudmock.core.spi.HttpMethod;
import io.cloudmock.core.spi.StubResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coverage for stateful (handler-based) stubs across all three protocols: a
 * {@link io.cloudmock.core.spi.StubHandler} runs at request time with access to the shared
 * {@code StateStore}, so what one request writes a later request reads back. Also pins that handler
 * stubs coexist with fault injection.
 */
class StatefulStubTest {

    // HttpClient is not AutoCloseable on the Java 17 baseline; hold it as a shared field.
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private final CloudMock cloudMock = new CloudMock();

    @AfterEach
    void tearDown() {
        cloudMock.stop();
    }

    /** Stateful service exercising all three protocols and the StubRequest accessors. */
    private static final class MixedService implements CloudMockService {
        @Override public String serviceId() { return "kv"; }
        @Override public void register(CloudMockContext context) {
            // JSON / X-Amz-Target
            context.registrar().registerJsonTargetStub("Test.Put", (req, store) -> {
                store.put("kv/value", req.body());
                return StubResponse.json("{\"ok\":true}");
            });
            context.registrar().registerJsonTargetStub("Test.Get", (req, store) -> {
                Object value = store.get("kv/value");
                return StubResponse.json("{\"value\":" + (value == null ? "null" : value) + "}");
            });
            // REST path — reads method/path/query/header off StubRequest, and sets a custom header.
            context.registrar().registerRestStub(HttpMethod.GET, "/echo/.*", (req, store) ->
                    StubResponse.json("{\"method\":\"" + req.method()
                                    + "\",\"path\":\"" + req.path()
                                    + "\",\"q\":\"" + req.queryParam("q")
                                    + "\",\"hdr\":\"" + req.header("X-Probe") + "\"}")
                            .withHeader("X-Custom", "yes"));
            // XML / Form URL
            context.registrar().registerXmlFormStub("StoreThing", (req, store) -> {
                store.put("kv/thing", req.body());
                return StubResponse.xml("<StoreThingResponse/>");
            });
        }
    }

    private HttpResponse<String> postJson(String target, String body) throws Exception {
        return HTTP.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + cloudMock.port() + "/"))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/x-amz-json-1.0")
                .header("X-Amz-Target", target)
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void jsonHandlerWritesAndReadsTheStore() throws Exception {
        cloudMock.withService(new MixedService());
        cloudMock.start();

        assertEquals("{\"value\":null}", postJson("Test.Get", "{}").body());

        HttpResponse<String> put = postJson("Test.Put", "{\"n\":42}");
        assertEquals(200, put.statusCode());
        assertEquals(StubResponse.CONTENT_TYPE_JSON,
                put.headers().firstValue("Content-Type").orElse(""),
                "StubResponse content type must be applied");
        assertEquals("{\"ok\":true}", put.body());

        assertEquals("{\"value\":{\"n\":42}}", postJson("Test.Get", "{}").body());
    }

    @Test
    void restHandlerSeesMethodPathQueryHeaderAndSetsCustomHeader() throws Exception {
        cloudMock.withService(new MixedService());
        cloudMock.start();

        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + cloudMock.port() + "/echo/abc?q=hello"))
                .header("X-Probe", "probed")
                .GET().build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("yes", response.headers().firstValue("X-Custom").orElse(""),
                "handler-set headers must reach the client");
        assertEquals("{\"method\":\"GET\",\"path\":\"/echo/abc\",\"q\":\"hello\",\"hdr\":\"probed\"}",
                response.body());
    }

    @Test
    void xmlFormHandlerRuns() throws Exception {
        cloudMock.withService(new MixedService());
        cloudMock.start();

        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + cloudMock.port() + "/"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("Action=StoreThing&Val=x"))
                .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals(StubResponse.CONTENT_TYPE_XML,
                response.headers().firstValue("Content-Type").orElse(""));
        assertTrue(response.body().contains("StoreThingResponse"));
    }

    @Test
    void handlerStubsCoexistWithThrottleFault() throws Exception {
        cloudMock.withService(new MixedService());
        cloudMock.start();

        cloudMock.simulateThrottle("kv");
        assertEquals(400, postJson("Test.Get", "{}").statusCode(),
                "throttle fault must override a handler stub");

        cloudMock.clearFaults("kv");
        assertEquals(200, postJson("Test.Get", "{}").statusCode(),
                "clearing the fault must restore the handler stub");
    }
}
