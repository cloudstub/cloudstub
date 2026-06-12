package io.cloudstub.core;

import static org.junit.jupiter.api.Assertions.*;

import io.cloudstub.core.spi.CloudStubContext;
import io.cloudstub.core.spi.CloudStubService;
import io.cloudstub.core.spi.HttpMethod;
import io.cloudstub.core.spi.StubResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Coverage for stateful (handler-based) stubs across all three protocols: a {@link
 * io.cloudstub.core.spi.StubHandler} runs at request time with access to the shared {@code
 * StateStore}, so what one request writes a later request reads back. Also pins that handler stubs
 * coexist with fault injection.
 */
class StatefulStubTest {

    // HttpClient is not AutoCloseable on the Java 17 baseline; hold it as a shared field.
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private final CloudStub cloudMock = new CloudStub();

    @AfterEach
    void tearDown() {
        cloudMock.stop();
    }

    /** Stateful service exercising all three protocols and the StubRequest accessors. */
    private static final class MixedService implements CloudStubService {
        @Override
        public String serviceId() {
            return "kv";
        }

        @Override
        public void register(CloudStubContext context) {
            // JSON / X-Amz-Target
            context.registrar()
                    .registerJsonTargetStub(
                            "Test.Put",
                            (req, store) -> {
                                store.put("kv/value", req.body());
                                return StubResponse.json("{\"ok\":true}");
                            });
            context.registrar()
                    .registerJsonTargetStub(
                            "Test.Get",
                            (req, store) -> {
                                Object value = store.get("kv/value");
                                return StubResponse.json(
                                        "{\"value\":" + (value == null ? "null" : value) + "}");
                            });
            // Increments a counter on every run — lets a test observe whether (and how often) the
            // handler executed under a fault.
            context.registrar()
                    .registerJsonTargetStub(
                            "Test.Incr",
                            (req, store) -> {
                                Object cur = store.get("kv/count");
                                int next = (cur == null ? 0 : (Integer) cur) + 1;
                                store.put("kv/count", next);
                                return StubResponse.json("{\"count\":" + next + "}");
                            });
            // REST path — reads method/path/query/header off StubRequest, and sets a custom header.
            context.registrar()
                    .registerRestStub(
                            HttpMethod.GET,
                            "/echo/.*",
                            (req, store) ->
                                    StubResponse.json(
                                                    "{\"method\":\""
                                                            + req.method()
                                                            + "\",\"path\":\""
                                                            + req.path()
                                                            + "\",\"q\":\""
                                                            + req.queryParam("q")
                                                            + "\",\"hdr\":\""
                                                            + req.header("X-Probe")
                                                            + "\"}")
                                            .withHeader("X-Custom", "yes"));
            // XML / Form URL
            context.registrar()
                    .registerXmlFormStub(
                            "StoreThing",
                            (req, store) -> {
                                store.put("kv/thing", req.body());
                                return StubResponse.xml("<StoreThingResponse/>");
                            });
        }
    }

    private HttpResponse<String> postJson(String target, String body) throws Exception {
        return HTTP.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + cloudMock.port() + "/"))
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .header("Content-Type", "application/x-amz-json-1.0")
                        .header("X-Amz-Target", target)
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void jsonHandlerWritesAndReadsTheStore() throws Exception {
        cloudMock.withService(new MixedService());
        cloudMock.start();

        assertEquals("{\"value\":null}", postJson("Test.Get", "{}").body());

        HttpResponse<String> put = postJson("Test.Put", "{\"n\":42}");
        assertEquals(200, put.statusCode());
        assertEquals(
                StubResponse.CONTENT_TYPE_JSON,
                put.headers().firstValue("Content-Type").orElse(""),
                "StubResponse content type must be applied");
        assertEquals("{\"ok\":true}", put.body());

        assertEquals("{\"value\":{\"n\":42}}", postJson("Test.Get", "{}").body());
    }

    @Test
    void restHandlerSeesMethodPathQueryHeaderAndSetsCustomHeader() throws Exception {
        cloudMock.withService(new MixedService());
        cloudMock.start();

        HttpResponse<String> response =
                HTTP.send(
                        HttpRequest.newBuilder()
                                .uri(
                                        URI.create(
                                                "http://localhost:"
                                                        + cloudMock.port()
                                                        + "/echo/abc?q=hello"))
                                .header("X-Probe", "probed")
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals(
                "yes",
                response.headers().firstValue("X-Custom").orElse(""),
                "handler-set headers must reach the client");
        assertEquals(
                "{\"method\":\"GET\",\"path\":\"/echo/abc\",\"q\":\"hello\",\"hdr\":\"probed\"}",
                response.body());
    }

    @Test
    void xmlFormHandlerRuns() throws Exception {
        cloudMock.withService(new MixedService());
        cloudMock.start();

        HttpResponse<String> response =
                HTTP.send(
                        HttpRequest.newBuilder()
                                .uri(URI.create("http://localhost:" + cloudMock.port() + "/"))
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .POST(
                                        HttpRequest.BodyPublishers.ofString(
                                                "Action=StoreThing&Val=x"))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals(
                StubResponse.CONTENT_TYPE_XML,
                response.headers().firstValue("Content-Type").orElse(""));
        assertTrue(response.body().contains("StoreThingResponse"));
    }

    @Test
    void handlerStubsCoexistWithThrottleFault() throws Exception {
        cloudMock.withService(new MixedService());
        cloudMock.start();

        cloudMock.simulateThrottle("kv");
        assertEquals(
                400,
                postJson("Test.Get", "{}").statusCode(),
                "throttle fault must override a handler stub");

        cloudMock.clearFaults("kv");
        assertEquals(
                200,
                postJson("Test.Get", "{}").statusCode(),
                "clearing the fault must restore the handler stub");
    }

    @Test
    void statefulHandlerRunsExactlyOncePerRequest() throws Exception {
        cloudMock.withService(new MixedService());
        cloudMock.start();

        assertEquals("{\"count\":1}", postJson("Test.Incr", "{}").body());
        assertEquals("{\"count\":2}", postJson("Test.Incr", "{}").body());
        assertEquals(
                2,
                cloudMock.stateStore().get("kv/count"),
                "each request must run the handler exactly once");
    }

    @Test
    void throttleFaultDoesNotRunStatefulHandler() throws Exception {
        cloudMock.withService(new MixedService());
        cloudMock.start();

        cloudMock.simulateThrottle("kv");
        assertEquals(400, postJson("Test.Incr", "{}").statusCode());
        assertNull(
                cloudMock.stateStore().get("kv/count"),
                "a throttle fault discards the body, so the handler must not run");
    }

    @Test
    void timeoutFaultDoesNotRunStatefulHandler() throws Exception {
        cloudMock.withService(new MixedService());
        cloudMock.start();

        cloudMock.simulateTimeout("kv");
        // The 30s server-side delay would hang the test; a short client timeout aborts first.
        assertThrows(
                IOException.class,
                () ->
                        HTTP.send(
                                HttpRequest.newBuilder()
                                        .uri(
                                                URI.create(
                                                        "http://localhost:"
                                                                + cloudMock.port()
                                                                + "/"))
                                        .timeout(Duration.ofMillis(500))
                                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                                        .header("Content-Type", "application/x-amz-json-1.0")
                                        .header("X-Amz-Target", "Test.Incr")
                                        .build(),
                                HttpResponse.BodyHandlers.ofString()));
        assertNull(
                cloudMock.stateStore().get("kv/count"),
                "a timeout fault discards the body, so the handler must not run");
    }

    @Test
    void brownoutAlwaysResetDoesNotRunStatefulHandler() throws Exception {
        cloudMock.withService(new MixedService());
        cloudMock.start();

        cloudMock.simulateNetworkBrownout("kv", 1.0);
        assertThrows(IOException.class, () -> postJson("Test.Incr", "{}"));
        assertNull(
                cloudMock.stateStore().get("kv/count"),
                "a full-rate brownout always resets, so the handler must not run");
    }

    @Test
    void probabilisticBrownoutRunsStatefulHandlerOncePerRequest() throws Exception {
        cloudMock.withService(new MixedService());
        cloudMock.start();

        // Under a probabilistic brownout the handler must run on every request — the pass-through
        // ones return real data, and a reset request that already wrote mirrors at-least-once
        // delivery. So regardless of which requests are reset, the counter equals the request
        // count.
        cloudMock.simulateNetworkBrownout("kv", 0.5);
        int requests = 24;
        for (int i = 0; i < requests; i++) {
            try {
                postJson("Test.Incr", "{}");
            } catch (IOException reset) {
                // connection reset by the brownout — the handler still ran server-side
            }
        }
        assertEquals(
                requests,
                cloudMock.stateStore().get("kv/count"),
                "the handler must run exactly once per request, reset or not");
    }
}
