package io.cloudstub.local.config.exception;

/** Signals an unusable standalone config file. */
public final class LocalConfigException extends RuntimeException {

    public LocalConfigException(String message) {
        super(message);
    }
}
