package io.cloudmock.core.spi;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable HTTP response returned by a {@link StubHandler}. Carries the status code, content type,
 * body string, and any additional response headers the networking driver should send back. No
 * WireMock type is exposed.
 *
 * <p>Use the {@link #json(String)} / {@link #xml(String)} factories for the common AWS wire formats,
 * or {@link #of(int, String, String)} for full control over status and content type. Add
 * protocol-specific headers (e.g. an S3 {@code ETag}) with {@link #withHeader(String, String)}.
 */
public final class StubResponse {

    /** Content type for AWS JSON-protocol services (SQS, Secrets Manager, DynamoDB). */
    public static final String CONTENT_TYPE_JSON = "application/x-amz-json-1.1";

    /** Content type for AWS query/XML-protocol services (SNS, legacy SQS). */
    public static final String CONTENT_TYPE_XML = "text/xml;charset=UTF-8";

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

    /** A {@code 200 OK} XML response with the AWS query/XML content type. */
    public static StubResponse xml(String body) {
        return new StubResponse(200, CONTENT_TYPE_XML, body, Map.of());
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
}
