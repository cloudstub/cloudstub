package io.cloudstub.core.download;

/** Thrown when a service module jar cannot be provisioned from Maven Central. */
public final class ModuleDownloadException extends RuntimeException {

    public ModuleDownloadException(String message) {
        super(message);
    }
}
