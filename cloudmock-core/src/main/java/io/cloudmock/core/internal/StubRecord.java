package io.cloudmock.core.internal;

/**
 * Immutable snapshot of one stub registration, used by {@link FaultEngine} to generate
 * matching fault stubs without reprocessing module code.
 *
 * <p>For REST stubs, {@code matchKey} encodes both method and path as {@code "METHOD pathPattern"}.
 */
record StubRecord(
        StubProtocol protocol,
        String matchKey,
        String responseTemplate,
        String contentType,
        int statusCode
) {}
