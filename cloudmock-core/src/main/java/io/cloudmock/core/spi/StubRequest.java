package io.cloudmock.core.spi;

/**
 * Read-only view of an incoming HTTP request, handed to a {@link StubHandler} at request time.
 *
 * <p>This is the only request abstraction in CloudMock's public SPI. It exposes just what a module
 * needs to translate an AWS API call into state-store operations — method, path, body, headers,
 * query parameters, and JSON body fields — without leaking any WireMock, servlet, or JSON-library
 * type. The implementation lives in {@code cloudmock-core}'s internal package and adapts the
 * underlying networking driver.
 */
public interface StubRequest {

    /** The HTTP method of the request. */
    HttpMethod method();

    /** The request path (without query string), e.g. {@code "/my-bucket/my-key"}. */
    String path();

    /** The raw request body as a string, or an empty string if there is none. */
    String body();

    /**
     * The value of the named request header, case-insensitively, or {@code null} if absent.
     *
     * @param name header name, e.g. {@code "X-Amz-Target"}
     */
    String header(String name);

    /**
     * The value of the named query-string parameter, or {@code null} if absent.
     *
     * @param name query parameter name
     */
    String queryParam(String name);

    /**
     * Reads a scalar field from the JSON request body, addressed by a dotted path, so JSON-protocol
     * modules (SQS, DynamoDB, Lambda, …) never have to write their own parser. The body is parsed
     * once per request.
     *
     * <p>The path is a sequence of segments separated by {@code '.'}; a leading {@code "$."} is
     * tolerated for familiarity with JSONPath. A numeric segment indexes into a JSON array. Examples:
     * {@code "QueueName"}, {@code "Item.id.S"}, {@code "Records.0.eventName"}, {@code "$.MessageBody"}.
     * String values are returned unescaped; numbers and booleans are returned in their JSON textual
     * form (e.g. {@code "10"}, {@code "true"}).
     *
     * <p>Returns {@code null} if the body is empty or not valid JSON, the path is empty or absent, or
     * the value at the path is JSON {@code null} or a non-scalar (object/array). It never throws on
     * malformed input — a bad body yields {@code null}, not an exception out of the handler.
     *
     * @param path dotted path to a scalar field, e.g. {@code "QueueName"}
     * @return the field value as a string, or {@code null}
     */
    String jsonField(String path);
}
