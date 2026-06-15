package io.cloudstub.local.config.exception;

/**
 * Signals an unusable standalone config file: an explicitly requested file that is absent, a file
 * that cannot be parsed, an unknown key, or a non-numeric value for a numeric key. The launcher
 * catches it, prints the message, and exits fast — the message names the file and the offending key
 * rather than surfacing a stack trace.
 */
public final class LocalConfigException extends RuntimeException {

    public LocalConfigException(String message) {
        super(message);
    }
}
