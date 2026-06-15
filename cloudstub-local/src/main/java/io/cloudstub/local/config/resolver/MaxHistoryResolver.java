package io.cloudstub.local.config.resolver;

import io.cloudstub.core.CloudStub;
import io.cloudstub.local.config.LocalConfig;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Resolves the cap on retained request-history entries for the long-lived local process.
 *
 * <p>Precedence: {@code --max-history=<n>} CLI flag, then {@code CLOUDSTUB_MAX_HISTORY} environment
 * variable, then the {@code cloudstub.max-history} config-file key, then {@link
 * CloudStub#DEFAULT_MAX_REQUEST_HISTORY}. A value of {@code 0} (or {@code unlimited} / {@code
 * none}) retains an unbounded history; a non-numeric flag or environment value is skipped, falling
 * through to the next source.
 */
public final class MaxHistoryResolver {

    private MaxHistoryResolver() {}

    static int resolve(String[] args) {
        return resolve(args, LocalConfig.empty());
    }

    public static int resolve(String[] args, LocalConfig config) {
        for (int i = 0; i < args.length; i++) {
            OptionalInt parsed = OptionalInt.empty();
            if (args[i].startsWith("--max-history=")) {
                parsed = parse(args[i].substring("--max-history=".length()));
            } else if ("--max-history".equals(args[i]) && i + 1 < args.length) {
                parsed = parse(args[i + 1]);
            }
            if (parsed.isPresent()) {
                return parsed.getAsInt();
            }
        }
        String env = System.getenv("CLOUDSTUB_MAX_HISTORY");
        if (env != null && !env.isBlank()) {
            OptionalInt parsed = parse(env);
            if (parsed.isPresent()) {
                return parsed.getAsInt();
            }
        }
        Optional<String> configured = config.get(LocalConfig.KEY_MAX_HISTORY);
        if (configured.isPresent()) {
            String trimmed = configured.get();
            if ("unlimited".equalsIgnoreCase(trimmed) || "none".equalsIgnoreCase(trimmed)) {
                return 0;
            }
            return config.getInt(LocalConfig.KEY_MAX_HISTORY).getAsInt();
        }
        return CloudStub.DEFAULT_MAX_REQUEST_HISTORY;
    }

    private static OptionalInt parse(String value) {
        String trimmed = value.trim();
        if ("unlimited".equalsIgnoreCase(trimmed) || "none".equalsIgnoreCase(trimmed)) {
            return OptionalInt.of(0);
        }
        try {
            return OptionalInt.of(Integer.parseInt(trimmed));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }
}
