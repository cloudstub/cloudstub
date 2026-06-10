package io.cloudmock.standalone;

import java.nio.file.Path;

/**
 * Resolves the directory used for persistent state in standalone mode.
 *
 * <p>Standalone persists state by default, so a restart keeps whatever the running services have
 * stored.
 *
 * <p>Precedence: {@code --store-dir=<path>} CLI flag, then {@code CLOUDMOCK_STORE_DIR} environment
 * variable, then the {@link #DEFAULT_STORE_DIR default directory}. Passing {@code none} (or
 * {@code off}) selects an in-memory store that leaves no files behind.
 */
final class StoreDirectoryResolver {

    static final String DEFAULT_STORE_DIR = ".cloudmock";

    private StoreDirectoryResolver() {}

    /** @return the directory for persistent state, or {@code null} for an in-memory store. */
    static Path resolve(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--store-dir=")) {
                return parse(args[i].substring("--store-dir=".length()));
            }
            if ("--store-dir".equals(args[i]) && i + 1 < args.length) {
                return parse(args[i + 1]);
            }
        }
        String env = System.getenv("CLOUDMOCK_STORE_DIR");
        if (env != null && !env.isBlank()) {
            return parse(env);
        }
        return Path.of(DEFAULT_STORE_DIR);
    }

    private static Path parse(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "none".equalsIgnoreCase(trimmed) || "off".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return Path.of(trimmed);
    }
}
