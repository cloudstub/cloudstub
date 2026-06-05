package io.cloudmock.core.exception;

/**
 * Thrown when the persistent state store cannot read or write its backing file.
 *
 * <p>State persistence is a hard requirement of the persistent store, so a write failure is
 * surfaced to the caller rather than silently swallowed.
 */
public final class CloudMockStateException extends RuntimeException {

    public CloudMockStateException(String message, Throwable cause) {
        super(message, cause);
    }
}
