package io.cloudstub.local.config.resolver;

import io.cloudstub.local.config.LocalConfig;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Resolves the directory used for persistent state in local mode.
 *
 * <p>Local persists state by default, so a restart keeps whatever the running services have stored.
 *
 * <p>Precedence: {@code --store-dir=<path>} CLI flag, then {@code CLOUDSTUB_STORE_DIR} environment
 * variable, then the {@code cloudstub.store-dir} config-file key, then the {@link
 * #DEFAULT_STORE_DIR default directory}. Passing {@code none} (or {@code off}) selects an in-memory
 * store that leaves no files behind.
 */
public final class StoreDirectoryResolver {

    static final String DEFAULT_STORE_DIR = ".cloudstub";

    private StoreDirectoryResolver() {}

    static Path resolve(String[] args) {
        return resolve(args, LocalConfig.empty());
    }

    /**
     * @return the directory for persistent state, or {@code null} for an in-memory store.
     */
    public static Path resolve(String[] args, LocalConfig config) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--store-dir=")) {
                return parse(args[i].substring("--store-dir=".length()));
            }
            if ("--store-dir".equals(args[i]) && i + 1 < args.length) {
                return parse(args[i + 1]);
            }
        }
        String env = System.getenv("CLOUDSTUB_STORE_DIR");
        if (env != null && !env.isBlank()) {
            return parse(env);
        }
        Optional<String> configured = config.get(LocalConfig.KEY_STORE_DIR);
        if (configured.isPresent()) {
            return parse(configured.get());
        }
        return Path.of(DEFAULT_STORE_DIR);
    }

    private static Path parse(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()
                || "none".equalsIgnoreCase(trimmed)
                || "off".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return Path.of(trimmed);
    }
}
