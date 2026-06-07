package io.cloudmock.core.internal;

/**
 * Immutable snapshot of one stub registration, used by {@link FaultEngine} to generate
 * matching fault stubs without reprocessing module code.
 *
 * <p>For REST stubs, {@code matchKey} encodes both method and path as {@code "METHOD pathPattern"}.
 *
 * <p>{@code handlerKey} is non-null for stateful (handler-based) stubs and identifies the handler in
 * {@link StatefulResponseTransformer}; it is {@code null} for template stubs. {@link FaultEngine}
 * uses it to keep the real handler running under timeout/brownout faults.
 */
record StubRecord(
        StubProtocol protocol,
        String matchKey,
        String responseTemplate,
        String contentType,
        int statusCode,
        String handlerKey
) {}
