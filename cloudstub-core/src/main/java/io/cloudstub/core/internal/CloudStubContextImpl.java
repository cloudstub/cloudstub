package io.cloudstub.core.internal;

import io.cloudstub.core.spi.CloudStubContext;
import io.cloudstub.core.spi.StateStore;
import io.cloudstub.core.spi.StubRegistrar;

public final class CloudStubContextImpl implements CloudStubContext {

    private final StubRegistrar registrar;
    private final StateStore stateStore;

    public CloudStubContextImpl(StubRegistrar registrar, StateStore stateStore) {
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
