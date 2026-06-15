package io.cloudstub.local.config.resolver;

import io.cloudstub.local.config.LocalConfig;
import java.util.OptionalInt;

/**
 * Resolves the port for the REST API server.
 *
 * <p>Precedence: {@code --api-port=<n>} CLI flag, then {@code CLOUDSTUB_API_PORT} environment
 * variable, then the {@code cloudstub.api-port} config-file key, then {@link #DEFAULT_API_PORT}. A
 * non-numeric flag or environment value is skipped, falling through to the next source.
 */
public final class ApiPortResolver {

    public static final int DEFAULT_API_PORT = 4567;

    private ApiPortResolver() {}

    static int resolve(String[] args) {
        return resolve(args, LocalConfig.empty());
    }

    public static int resolve(String[] args, LocalConfig config) {
        for (int i = 0; i < args.length; i++) {
            OptionalInt parsed = OptionalInt.empty();
            if (args[i].startsWith("--api-port=")) {
                parsed = tryParse(args[i].substring("--api-port=".length()));
            } else if ("--api-port".equals(args[i]) && i + 1 < args.length) {
                parsed = tryParse(args[i + 1]);
            }
            if (parsed.isPresent()) {
                return parsed.getAsInt();
            }
        }
        String env = System.getenv("CLOUDSTUB_API_PORT");
        if (env != null && !env.isBlank()) {
            OptionalInt parsed = tryParse(env);
            if (parsed.isPresent()) {
                return parsed.getAsInt();
            }
        }
        return config.getInt(LocalConfig.KEY_API_PORT).orElse(DEFAULT_API_PORT);
    }

    private static OptionalInt tryParse(String value) {
        try {
            return OptionalInt.of(Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }
}
