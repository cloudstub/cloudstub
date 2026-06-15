package io.cloudstub.local.config.resolver;

import io.cloudstub.local.config.LocalConfig;

/**
 * Resolves the port the mock server binds.
 *
 * <p>Precedence: {@code --port=<n>} CLI flag, then {@code CLOUDSTUB_PORT} environment variable,
 * then the {@code cloudstub.port} config-file key, then {@link #DEFAULT_PORT}.
 */
public final class PortResolver {

    public static final int DEFAULT_PORT = 4566;

    private PortResolver() {}

    static int resolve(String[] args) {
        return resolve(args, LocalConfig.empty());
    }

    public static int resolve(String[] args, LocalConfig config) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--port=")) {
                return Integer.parseInt(args[i].substring("--port=".length()));
            }
            if ("--port".equals(args[i]) && i + 1 < args.length) {
                return Integer.parseInt(args[i + 1]);
            }
        }
        String envPort = System.getenv("CLOUDSTUB_PORT");
        if (envPort != null && !envPort.isBlank()) {
            return Integer.parseInt(envPort);
        }
        return config.getInt(LocalConfig.KEY_PORT).orElse(DEFAULT_PORT);
    }
}
