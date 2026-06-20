package io.cloudstub.core.spi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable HTTP response returned by a {@link StubHandler}. Carries the status code, content type,
 * body string, and any additional response headers the networking driver should send back. No
 * WireMock type is exposed.
 *
 * <p>Use {@link #json(Map)} to serialise a response body from plain JDK collections (the engine
 * does the JSON encoding and escaping, so handlers never hand-write JSON), {@link #json(String)} /
 * {@link #xml(String)} when a handler already holds a body string, or {@link #of(int, String,
 * String)} for full control over status and content type. Add protocol-specific headers (e.g. an S3
 * {@code ETag}) with {@link #withHeader(String, String)}.
 */
public final class StubResponse {

    /** Content type for AWS JSON-protocol services (SQS, Secrets Manager, DynamoDB). */
    public static final String CONTENT_TYPE_JSON = "application/x-amz-json-1.1";

    /** Content type for AWS query/XML-protocol services (SNS, legacy SQS). */
    public static final String CONTENT_TYPE_XML = "text/xml;charset=UTF-8";

    /**
     * Plain mapper for response bodies — no default typing, so a {@code Map}/{@code List} tree
     * serialises to ordinary JSON. Shaded into {@code io.cloudstub.shaded.jackson}, so no jackson
     * type ever reaches a module.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final int status;
    private final String contentType;
    private final String body;
    private final Map<String, String> headers;

    private StubResponse(int status, String contentType, String body, Map<String, String> headers) {
        this.status = status;
        this.contentType = Objects.requireNonNull(contentType, "contentType must not be null");
        this.body = Objects.requireNonNull(body, "body must not be null");
        this.headers = headers;
    }

    /** A {@code 200 OK} JSON response with the AWS JSON content type. */
    public static StubResponse json(String body) {
        return new StubResponse(200, CONTENT_TYPE_JSON, body, Map.of());
    }

    /**
     * A {@code 200 OK} JSON response whose body is serialised from a JDK map/list/value tree. The
     * engine performs the JSON encoding and string escaping, so handlers build the response from
     * {@code Map}, {@code List}, {@code String}, and number/boolean values instead of concatenating
     * JSON by hand.
     *
     * @param body the response object; values must be JSON-serialisable JDK types
     * @throws IllegalArgumentException if {@code body} cannot be serialised to JSON
     */
    public static StubResponse json(Map<String, ?> body) {
        return json(200, body);
    }

    /**
     * A JSON response with an explicit status, serialised from a JDK map/list/value tree (see
     * {@link #json(Map)}). Use for AWS JSON-protocol error responses, e.g. a {@code 400} carrying
     * {@code __type} and {@code Message}.
     *
     * @throws IllegalArgumentException if {@code body} cannot be serialised to JSON
     */
    public static StubResponse json(int status, Map<String, ?> body) {
        return new StubResponse(status, CONTENT_TYPE_JSON, serialize(body), Map.of());
    }

    /** A {@code 200 OK} XML response with the AWS query/XML content type. */
    public static StubResponse xml(String body) {
        return new StubResponse(200, CONTENT_TYPE_XML, body, Map.of());
    }

    /**
     * A {@code 200 OK} XML response whose body is rendered from an {@link XmlElement} tree. The
     * engine performs the XML encoding and escaping, so handlers describe the element structure
     * instead of concatenating XML by hand.
     */
    public static StubResponse xml(XmlElement root) {
        return new StubResponse(200, CONTENT_TYPE_XML, root.render(), Map.of());
    }

    /** A response with an explicit status, content type, and body. */
    public static StubResponse of(int status, String contentType, String body) {
        return new StubResponse(status, contentType, body, Map.of());
    }

    /**
     * Returns a copy of this response with an additional header. The {@code Content-Type} header is
     * managed via {@link #contentType()} and should be set through the factories, not here.
     */
    public StubResponse withHeader(String name, String value) {
        Map<String, String> next = new LinkedHashMap<>(headers);
        next.put(name, value);
        return new StubResponse(status, contentType, body, Map.copyOf(next));
    }

    public int status() {
        return status;
    }

    public String contentType() {
        return contentType;
    }

    public String body() {
        return body;
    }

    /** Additional response headers beyond {@code Content-Type}; never null, possibly empty. */
    public Map<String, String> headers() {
        return headers;
    }

    private static String serialize(Map<String, ?> body) {
        try {
            return MAPPER.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("response body is not JSON-serialisable", e);
        }
    }
}
