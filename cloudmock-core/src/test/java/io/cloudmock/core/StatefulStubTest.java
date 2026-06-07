package io.cloudmock.core;

import io.cloudmock.core.spi.CloudMockContext;
import io.cloudmock.core.spi.CloudMockService;
import io.cloudmock.core.spi.StubResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coverage for stateful (handler-based) stubs: a {@link io.cloudmock.core.spi.StubHandler} runs at
 * request time with access to the shared {@code StateStore}, so what one request writes a later
 * request reads back. Also pins that handler stubs coexist with fault injection.
 */
class StatefulStubTest {

    private final CloudMock cloudMock = new CloudMock();

    @AfterEach
    void tearDown() {
        cloudMock.stop();
    }

    /** A tiny stateful service: {@code Test.Put} stores the body, {@code Test.Get} returns it. */
    private static final class KeyValueService implements CloudMockService {
        @Override public String serviceId() { return "kv"; }
        @Override public void register(CloudMockContext context) {
            context.registrar().registerJsonTargetStub("Test.Put", (req, store) -> {
                store.put("kv/value", req.body());
                return StubResponse.json("{\"ok\":true}");
            });
            context.registrar().registerJsonTargetStub("Test.Get", (req, store) -> {
                Object value = store.get("kv/value");
                return StubResponse.json("{\"value\":" + (value == null ? "null" : value) + "}");
            });
        }
    }

    private HttpResponse<String> post(String target, String body) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + cloudMock.port() + "/"))
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .header("Content-Type", "application/x-amz-json-1.0")
                        .header("X-Amz-Target", target)
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void handlerWritesAndReadsTheStore() throws Exception {
        cloudMock.withService(new KeyValueService());
        cloudMock.start();

        // Read-before-write returns the empty value.
        assertEquals("{\"value\":null}", post("Test.Get", "{}").body());

        HttpResponse<String> put = post("Test.Put", "{\"n\":42}");
        assertEquals(200, put.statusCode());
        assertEquals("application/x-amz-json-1.0", put.headers().firstValue("Content-Type").orElse(""),
                "StubResponse content type must be applied");
        assertEquals("{\"ok\":true}", put.body());

        // The store now reflects what the previous request wrote.
        assertEquals("{\"value\":{\"n\":42}}", post("Test.Get", "{}").body());
    }

    @Test
    void handlerStubsCoexistWithThrottleFault() throws Exception {
        cloudMock.withService(new KeyValueService());
        cloudMock.start();

        cloudMock.simulateThrottle("kv");
        assertEquals(400, post("Test.Get", "{}").statusCode(),
                "throttle fault must override a handler stub");

        cloudMock.clearFaults("kv");
        assertEquals(200, post("Test.Get", "{}").statusCode(),
                "clearing the fault must restore the handler stub");
    }
}
