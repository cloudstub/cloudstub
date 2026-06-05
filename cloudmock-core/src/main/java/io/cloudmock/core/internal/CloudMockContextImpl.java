package io.cloudmock.core.internal;

import io.cloudmock.core.spi.CloudMockContext;
import io.cloudmock.core.spi.StateStore;
import io.cloudmock.core.spi.StubRegistrar;

public final class CloudMockContextImpl implements CloudMockContext {

    private final StubRegistrar registrar;
    private final StateStore stateStore;

    public CloudMockContextImpl(StubRegistrar registrar, StateStore stateStore) {
        this.registrar = registrar;
        this.stateStore = stateStore;
    }

    @Override
    public StubRegistrar registrar() {
        return registrar;
    }

    @Override
    public StateStore stateStore() {
        return stateStore;
    }
}
