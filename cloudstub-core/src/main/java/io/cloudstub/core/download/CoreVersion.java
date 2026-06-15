package io.cloudstub.core.download;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Reports the running {@code cloudstub-core} version from a build-stamped classpath resource. */
public final class CoreVersion {

    private static final String VERSION_RESOURCE = "version.properties";

    private CoreVersion() {}

    /**
     * @return the running core version (the project version stamped into the jar at build time)
     * @throws IllegalStateException if the version resource is absent or malformed
     */
    public static String current() {
        try (InputStream in = CoreVersion.class.getResourceAsStream(VERSION_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(
                        "Missing core version resource — the build did not stamp the version.");
            }
            Properties props = new Properties();
            props.load(in);
            String version = props.getProperty("version");
            if (version == null || version.isBlank()) {
                throw new IllegalStateException("Core version resource has no 'version' property.");
            }
            return version.trim();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read core version resource", e);
        }
    }
}
