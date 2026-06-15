package io.cloudstub.core.download;

import java.nio.file.Path;

/** Thrown when a service module jar cannot be provisioned from Maven Central. */
public final class ModuleDownloadException extends RuntimeException {

    public ModuleDownloadException(String message) {
        super(message);
    }

    static ModuleDownloadException provisioning(
            MavenModuleCoordinate coordinate, Path dir, String reason) {
        return new ModuleDownloadException(
                "could not provision service '"
                        + coordinate.service()
                        + "' from "
                        + coordinate.displayCoordinate()
                        + " — "
                        + reason
                        + ".\n"
                        + "            Place the jar manually in "
                        + dir.toAbsolutePath()
                        + " and restart, or disable auto-download with --no-download /"
                        + " CLOUDSTUB_AUTO_DOWNLOAD=false.");
    }
}
