package io.cloudstub.core.exception;

public final class CloudStubAlreadyStartedException extends IllegalStateException {

    public CloudStubAlreadyStartedException() {
        super("CloudStub is already started. Call stop() before starting again.");
    }
}
