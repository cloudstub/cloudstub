package io.cloudmock.core.exception;

public final class CloudMockNotStartedException extends IllegalStateException {

    public CloudMockNotStartedException() {
        super("CloudMock is not started.");
    }
}
