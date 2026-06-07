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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Coverage for {@code StubRequest.jsonField} — the shared request-body accessor that replaces
 * per-module JSON parsing (issue #0044/#0045). Exercised end to end through a stateful stub so the
 * internal jackson-backed implementation is what runs.
 */
class JsonFieldTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private final CloudMock cloudMock = new CloudMock();

    @AfterEach
    void tearDown() {
        cloudMock.stop();
    }

    /** Each stub echoes one jsonField path back as plain text ({@code <null>} when absent). */
    private static final class ProbeService implements CloudMockService {
        @Override public String serviceId() { return "probe"; }
        @Override public void register(CloudMockContext context) {
            echo(context, "Probe.A", "a");
            echo(context, "Probe.NestedB", "nested.b");
            echo(context, "Probe.Num", "num");
            echo(context, "Probe.Flag", "flag");
            echo(context, "Probe.Missing", "missing");
            echo(context, "Probe.DollarA", "$.a");
            echo(context, "Probe.Prefix", "prefix");
            echo(context, "Probe.PrefixLong", "prefixLong");
            echo(context, "Probe.ArrayIdx", "items.1.name");
            echo(context, "Probe.Empty", "");
            echo(context, "Probe.DollarOnly", "$.");
        }
        private static void echo(CloudMockContext ctx, String target, String path) {
            ctx.registrar().registerJsonTargetStub(target, (req, store) -> {
                String value = req.jsonField(path);
                return StubResponse.of(200, "text/plain", value == null ? "<null>" : value);
            });
        }
    }

    private String probe(String target, String body) throws Exception {
        return HTTP.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + cloudMock.port() + "/"))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/x-amz-json-1.0")
                .header("X-Amz-Target", target)
                .build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    @Test
    void extractsScalarsNestedPathsAndHonoursEscapesAndPrefixes() throws Exception {
        cloudMock.withService(new ProbeService());
        cloudMock.start();

        String body = "{\"a\":\"hi \\\"q\\\"\\nend\","
                + "\"nested\":{\"b\":\"deep\"},"
                + "\"num\":42,\"flag\":true,"
                + "\"prefix\":\"P\",\"prefixLong\":\"PL\"}";

        assertEquals("hi \"q\"\nend", probe("Probe.A", body), "string value must be unescaped");
        assertEquals("deep", probe("Probe.NestedB", body), "dotted path must navigate objects");
        assertEquals("42", probe("Probe.Num", body), "numbers come back in textual form");
        assertEquals("true", probe("Probe.Flag", body), "booleans come back in textual form");
        assertEquals("hi \"q\"\nend", probe("Probe.DollarA", body), "leading $. is tolerated");
        assertEquals("P", probe("Probe.Prefix", body));
        assertEquals("PL", probe("Probe.PrefixLong", body),
                "a field whose name is a prefix of another must not be confused");
    }

    @Test
    void absentPathReturnsNull() throws Exception {
        cloudMock.withService(new ProbeService());
        cloudMock.start();
        assertEquals("<null>", probe("Probe.Missing", "{\"a\":\"x\"}"));
        assertEquals("<null>", probe("Probe.NestedB", "{\"a\":\"x\"}"));
    }

    @Test
    void numericSegmentIndexesIntoArray() throws Exception {
        cloudMock.withService(new ProbeService());
        cloudMock.start();
        assertEquals("second",
                probe("Probe.ArrayIdx", "{\"items\":[{\"name\":\"first\"},{\"name\":\"second\"}]}"));
    }

    @Test
    void emptyOrDollarOnlyPathReturnsNull() throws Exception {
        cloudMock.withService(new ProbeService());
        cloudMock.start();
        // An empty path must not resolve to the whole document.
        assertEquals("<null>", probe("Probe.Empty", "{\"a\":\"x\"}"));
        assertEquals("<null>", probe("Probe.DollarOnly", "{\"a\":\"x\"}"));
    }

    @Test
    void malformedBodyReturnsNullRatherThanThrowing() throws Exception {
        cloudMock.withService(new ProbeService());
        cloudMock.start();
        assertEquals("<null>", probe("Probe.A", "not json {"));
        assertEquals("<null>", probe("Probe.A", ""));
    }
}
