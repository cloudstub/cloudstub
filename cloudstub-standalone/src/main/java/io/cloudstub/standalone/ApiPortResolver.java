package io.cloudstub.standalone;

/**
 * Resolves the port for the REST API server.
 *
 * <p>Precedence: {@code --api-port=<n>} CLI flag, then {@code CLOUDSTUB_API_PORT} environment
 * variable, then {@link #DEFAULT_API_PORT}.
 */
final class ApiPortResolver {

    static final int DEFAULT_API_PORT = 4567;

    private ApiPortResolver() {}

    static int resolve(String[] args) {
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
        return DEFAULT_API_PORT;
    }
}
