package io.cloudstub.local.config.resolver;

import io.cloudstub.local.config.LocalConfig;
import java.util.OptionalInt;

/**
 * Resolves the port the mock server binds.
 *
 * <p>Precedence: {@code --port=<n>} CLI flag, then {@code CLOUDSTUB_PORT} environment variable,
 * then the {@code cloudstub.port} config-file key, then {@link #DEFAULT_PORT}. A non-numeric flag
 * or environment value is skipped, falling through to the next source.
 */
public final class PortResolver {

    public static final int DEFAULT_PORT = 4566;

    private PortResolver() {}

    static int resolve(String[] args) {
        return resolve(args, LocalConfig.empty());
    }

    public static int resolve(String[] args, LocalConfig config) {
        for (int i = 0; i < args.length; i++) {
            OptionalInt parsed = OptionalInt.empty();
            if (args[i].startsWith("--port=")) {
                parsed = tryParse(args[i].substring("--port=".length()));
            } else if ("--port".equals(args[i]) && i + 1 < args.length) {
                parsed = tryParse(args[i + 1]);
            }
            if (parsed.isPresent()) {
                return parsed.getAsInt();
            }
        }
        String envPort = System.getenv("CLOUDSTUB_PORT");
        if (envPort != null && !envPort.isBlank()) {
            OptionalInt parsed = tryParse(envPort);
            if (parsed.isPresent()) {
                return parsed.getAsInt();
            }
        }
        return config.getInt(LocalConfig.KEY_PORT).orElse(DEFAULT_PORT);
    }

    private static OptionalInt tryParse(String value) {
        try {
            return OptionalInt.of(Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }
}
