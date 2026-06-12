package io.cloudstub.core.exception;

public final class CloudStubNotStartedException extends IllegalStateException {

    public CloudStubNotStartedException() {
        super("CloudStub is not started.");
    }
}
