package io.cloudmock.core.exception;

public final class CloudMockAlreadyStartedException extends IllegalStateException {

    public CloudMockAlreadyStartedException() {
        super("CloudMock is already started. Call stop() before starting again.");
    }
}
