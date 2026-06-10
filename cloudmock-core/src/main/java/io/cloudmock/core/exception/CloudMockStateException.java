package io.cloudmock.core.exception;

/**
 * Thrown when the persistent state store cannot read or write its backing file.
 */
public final class CloudMockStateException extends RuntimeException {

    public CloudMockStateException(String message, Throwable cause) {
        super(message, cause);
    }
}
