package io.cloudmock.core.spi.restapi;

import io.cloudmock.core.spi.StateStore;

/**
 * Passed to {@link io.cloudmock.core.spi.CloudMockApiService#registerRoutes} at API-server startup.
 *
 * <p>Mirrors {@link io.cloudmock.core.spi.CloudMockContext} on the stub side: it carries the route
 * registrar plus the shared {@link StateStore}, so a module's REST routes read and write the same
 * live data the AWS-protocol stubs do. A message stored by the AWS path is therefore visible through
 * the REST API (and the console), and vice versa.
 *
 * <p>The {@link StateStore} returned here is the <em>same instance</em> handed to the module's
 * {@link io.cloudmock.core.spi.CloudMockService} via {@code CloudMockContext}, so both surfaces share
 * one source of truth.
 */
public interface CloudMockApiContext {

    /** The registrar through which the module declares its API routes. */
    ApiRouteRegistrar registrar();

    /** The shared state store; the same instance the module's stubs use. */
    StateStore stateStore();
}
