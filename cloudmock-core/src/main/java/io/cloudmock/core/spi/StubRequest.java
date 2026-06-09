package io.cloudmock.core.spi;

/**
 * Read-only view of an incoming HTTP request, handed to a {@link StubHandler} at request time.
 *
 * <p>This is the only request abstraction in CloudMock's public SPI. It exposes just what a module
 * needs to translate an AWS API call into state-store operations — method, path, body, headers, and
 * query parameters — without leaking any WireMock or servlet type. The implementation lives in
 * {@code cloudmock-core}'s internal package and adapts the underlying networking driver.
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
}
