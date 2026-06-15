package io.cloudstub.local.config.resolver;

import io.cloudstub.local.config.LocalConfig;

/**
 * Resolves the port for the REST API server.
 *
 * <p>Precedence: {@code --api-port=<n>} CLI flag, then {@code CLOUDSTUB_API_PORT} environment
 * variable, then the {@code cloudstub.api-port} config-file key, then {@link #DEFAULT_API_PORT}.
 */
public final class ApiPortResolver {

    public static final int DEFAULT_API_PORT = 4567;

    private ApiPortResolver() {}

    static int resolve(String[] args) {
        return resolve(args, LocalConfig.empty());
    }

    public static int resolve(String[] args, LocalConfig config) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--api-port=")) {
                return Integer.parseInt(args[i].substring("--api-port=".length()));
            }
            if ("--api-port".equals(args[i]) && i + 1 < args.length) {
                return Integer.parseInt(args[i + 1]);
            }
        }
        String env = System.getenv("CLOUDSTUB_API_PORT");
        if (env != null && !env.isBlank()) {
            return Integer.parseInt(env);
        }
        return config.getInt(LocalConfig.KEY_API_PORT).orElse(DEFAULT_API_PORT);
    }
}
