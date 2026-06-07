package io.cloudmock.core.spi;

import java.util.Objects;

/**
 * Immutable HTTP response returned by a {@link StubHandler}. Carries the status code, content type,
 * and body string the networking driver should send back. No WireMock type is exposed.
 *
 * <p>Use the {@link #json(String)} / {@link #xml(String)} factories for the common AWS wire formats,
 * or {@link #of(int, String, String)} for full control over status and content type.
 */
public final class StubResponse {

    /** Content type for AWS JSON-protocol services (SQS, DynamoDB). */
    public static final String CONTENT_TYPE_JSON = "application/x-amz-json-1.0";

    /** Content type for AWS query/XML-protocol services (SNS, legacy SQS). */
    public static final String CONTENT_TYPE_XML = "text/xml;charset=UTF-8";

    private final int status;
    private final String contentType;
    private final String body;

    private StubResponse(int status, String contentType, String body) {
        this.status = status;
        this.contentType = Objects.requireNonNull(contentType, "contentType must not be null");
        this.body = Objects.requireNonNull(body, "body must not be null");
    }

    /** A {@code 200 OK} JSON response with the AWS JSON content type. */
    public static StubResponse json(String body) {
        return new StubResponse(200, CONTENT_TYPE_JSON, body);
    }

    /** A {@code 200 OK} XML response with the AWS query/XML content type. */
    public static StubResponse xml(String body) {
        return new StubResponse(200, CONTENT_TYPE_XML, body);
    }

    /** A response with an explicit status, content type, and body. */
    public static StubResponse of(int status, String contentType, String body) {
        return new StubResponse(status, contentType, body);
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
}
