package io.cloudmock.core.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.http.QueryParameter;
import com.github.tomakehurst.wiremock.http.Request;
import io.cloudmock.core.spi.HttpMethod;
import io.cloudmock.core.spi.StubRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapts a WireMock {@link Request} to the public {@link StubRequest} SPI view, so that
 * {@link io.cloudmock.core.spi.StubHandler}s never see a WireMock or JSON-library type.
 *
 * <p>The body string and its parsed JSON tree are read/parsed lazily and cached, so a handler that
 * pulls several fields out of one request pays for the read and parse only once.
 */
final class WireMockStubRequest implements StubRequest {

    private static final Logger log = LoggerFactory.getLogger(WireMockStubRequest.class);

    /**
     * A plain, read-only mapper shared across requests; thread-safe for {@code readTree}. Deliberately
     * NOT the one in {@code JsonFileStateStore}, whose default typing would mis-parse plain request
     * bodies.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Request request;

    private String bodyString;
    private boolean bodyParsed;
    private JsonNode bodyTree;

    WireMockStubRequest(Request request) {
        this.request = request;
    }

    @Override
    public HttpMethod method() {
        return HttpMethod.valueOf(request.getMethod().value());
    }

    @Override
    public String path() {
        String url = request.getUrl();
        int q = url.indexOf('?');
        return q >= 0 ? url.substring(0, q) : url;
    }

    @Override
    public String body() {
        if (bodyString == null) {
            String raw = request.getBodyAsString();
            bodyString = raw != null ? raw : "";
        }
        return bodyString;
    }

    @Override
    public String header(String name) {
        return request.getHeader(name);
    }

    @Override
    public String queryParam(String name) {
        QueryParameter param = request.queryParameter(name);
        return param != null && param.isPresent() ? param.firstValue() : null;
    }

    @Override
    public String jsonField(String path) {
        JsonNode root = bodyTree();
        if (root == null || path == null) {
            return null;
        }
        String dotted = path.startsWith("$.") ? path.substring(2) : path;
        if (dotted.isEmpty()) {
            return null;
        }
        // Reuse jackson's JSON Pointer instead of a bespoke walker: it navigates objects and array
        // indices (e.g. "Items.0.id") and never throws — a missing path resolves to a MissingNode.
        JsonNode node = root.at("/" + dotted.replace('.', '/'));
        // Only scalars are returned as text; objects/arrays/missing/explicit null map to absent.
        return node.isValueNode() && !node.isNull() ? node.asText() : null;
    }

    /** Lazily parses the body once; a non-JSON or empty body yields {@code null} (never throws). */
    private JsonNode bodyTree() {
        if (!bodyParsed) {
            bodyParsed = true;
            String raw = body();
            if (!raw.isEmpty()) {
                try {
                    bodyTree = MAPPER.readTree(raw);
                } catch (Exception e) {
                    // A non-JSON or malformed body is not an error — handlers just get null. Log at
                    // debug so a developer chasing a stateful round-trip can see why a field was empty.
                    log.debug("Request body is not valid JSON; jsonField will return null: {}",
                            e.getMessage());
                    bodyTree = null;
                }
            }
        }
        return bodyTree;
    }
}
