package io.cloudstub.core.spi;

import io.cloudstub.core.spi.restapi.CloudStubApiContext;

/**
 * Optional companion to {@link CloudStubService} for modules that want to expose API routes under
 * {@code /api/<serviceId>/…}.
 *
 * <p>Discovered at startup via {@code ServiceLoader.load(CloudStubApiService.class)}. Modules that
 * have nothing to expose simply do not implement this interface.
 *
 * <p>Registered via {@code META-INF/services/io.cloudstub.core.spi.CloudStubApiService}.
 */
public interface CloudStubApiService {

    /** Must match the {@link CloudStubService#serviceId()} of the same module. */
    String serviceId();

    /**
     * Called at API server startup. Register all module-specific routes through {@code
     * context.registrar()}; routes are mounted at {@code /api/<serviceId><path>}. The same {@code
     * context.stateStore()} the module's stubs use is available here, so REST routes can return
     * live data instead of synthetic stubs.
     */
    void registerRoutes(CloudStubApiContext context);
}
