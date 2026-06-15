package io.cloudstub.local.config.resolver;

import io.cloudstub.core.CloudStub;
import io.cloudstub.local.config.LocalConfig;
import java.util.Optional;

/**
 * Resolves the cap on retained request-history entries for the long-lived local process.
 *
 * <p>Precedence: {@code --max-history=<n>} CLI flag, then {@code CLOUDSTUB_MAX_HISTORY} environment
 * variable, then the {@code cloudstub.max-history} config-file key, then {@link
 * CloudStub#DEFAULT_MAX_REQUEST_HISTORY}. A value of {@code 0} (or {@code unlimited} / {@code
 * none}) retains an unbounded history.
 */
public final class MaxHistoryResolver {

    private MaxHistoryResolver() {}

    static int resolve(String[] args) {
        return resolve(args, LocalConfig.empty());
    }

    public static int resolve(String[] args, LocalConfig config) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--max-history=")) {
                return parse(args[i].substring("--max-history=".length()));
            }
            if ("--max-history".equals(args[i]) && i + 1 < args.length) {
                return parse(args[i + 1]);
            }
        }
        String env = System.getenv("CLOUDSTUB_MAX_HISTORY");
        if (env != null && !env.isBlank()) {
            return parse(env);
        }
        Optional<String> configured = config.get(LocalConfig.KEY_MAX_HISTORY);
        if (configured.isPresent()) {
            String trimmed = configured.get();
            if ("unlimited".equalsIgnoreCase(trimmed) || "none".equalsIgnoreCase(trimmed)) {
                return 0;
            }
            // Delegate the numeric parse to LocalConfig so a non-numeric value fails fast with a
            // message naming the file and key rather than an unguarded NumberFormatException.
            return config.getInt(LocalConfig.KEY_MAX_HISTORY).getAsInt();
        }
        return CloudStub.DEFAULT_MAX_REQUEST_HISTORY;
    }

    private static int parse(String value) {
        String trimmed = value.trim();
        if ("unlimited".equalsIgnoreCase(trimmed) || "none".equalsIgnoreCase(trimmed)) {
            return 0;
        }
        return Integer.parseInt(trimmed);
    }
}
