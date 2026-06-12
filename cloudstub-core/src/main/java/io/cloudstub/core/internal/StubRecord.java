package io.cloudstub.core.internal;

/**
 * Immutable snapshot of one stub registration. Used by {@link CloudStubResponseTransformer} to
 * recover a matched stub's protocol when decorating its response with a fault, and by the registrar
 * to report registered stubs in {@code /api/status}.
 *
 * <p>For REST stubs, {@code matchKey} encodes both method and path as {@code "METHOD pathPattern"}.
 */
record StubRecord(StubProtocol protocol, String matchKey) {}
