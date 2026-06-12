package io.cloudstub.core.exception;

/** Thrown when the persistent state store cannot read or write its backing file. */
public final class CloudStubStateException extends RuntimeException {

    public CloudStubStateException(String message, Throwable cause) {
        super(message, cause);
    }
}
