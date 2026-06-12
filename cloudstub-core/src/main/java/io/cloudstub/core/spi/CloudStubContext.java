package io.cloudstub.core.spi;

/**
 * Passed to {@link CloudStubService#register(CloudStubContext)} at startup.
 *
 * <p>Carries the two objects a module needs during registration: the stub registrar for wiring HTTP
 * stubs, and the state store for reading and writing live data at request time.
 *
 * <p>Adding future capabilities (metrics, config) extends this interface without touching the
 * {@link CloudStubService} signature.
 */
public interface CloudStubContext {

    /**
     * The registrar through which the module declares its HTTP stubs.
     *
     * @return the stub registrar for this registration pass
     */
    StubRegistrar registrar();

    /**
     * The shared state store. Modules read and write under their own service-ID prefix; see {@link
     * StateStore} for the key naming convention.
     *
     * @return the shared state store
     */
    StateStore stateStore();
}
