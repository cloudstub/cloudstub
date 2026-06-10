package io.cloudmock.core.spi;

/**
 * A request-time handler for a stateful stub. Receives the incoming {@link StubRequest} and the
 * shared {@link StateStore}, performs whatever reads and writes the AWS operation implies, and
 * returns the {@link StubResponse} to send back.
 *
 * <p>A handler for {@code SendMessage} writes the message to the store, and a handler for
 * {@code ReceiveMessage} reads it back, so what a user sends in one call is returned by the next.
 *
 * <p>Handlers must depend only on the core SPI types and the JDK — no WireMock, AWS SDK, jackson, or
 * other library type may appear in their signature or implementation surface. They run on WireMock's
 * request threads and may be invoked concurrently, so any shared mutable state they hold must be
 * thread-safe; the {@link StateStore} itself already is.
 */
@FunctionalInterface
public interface StubHandler {

    /**
     * Handle a matched request.
     *
     * @param request the incoming request
     * @param store   the shared state store, scoped by service-ID prefix by convention
     * @return the response to send back
     */
    StubResponse handle(StubRequest request, StateStore store);
}
