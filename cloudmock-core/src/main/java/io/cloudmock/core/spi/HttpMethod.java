package io.cloudmock.core.spi;

/**
 * HTTP methods used to match REST-style stubs via {@link StubRegistrar#registerRestStub}.
 * This enum is the only HTTP-method abstraction in CloudMock's public API; no WireMock
 * or servlet types appear here.
 */
public enum HttpMethod {
    GET, POST, PUT, DELETE, HEAD, PATCH
}
