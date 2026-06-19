package io.cloudstub.local.cli;

/**
 * Connection settings for a running CloudStub instance.
 *
 * <p>Extracted from the raw argument list before picocli parsing so the CLI can fetch {@code
 * /api/status} and build its command tree dynamically. The CLI talks only to the REST API port;
 * defaults match standalone mode (API 4567) and can be overridden by {@code --host} / {@code
 * --api-port} or the {@code CLOUDSTUB_HOST} / {@code CLOUDSTUB_API_PORT} environment variables.
 */
public record Conn(String host, int apiPort) {

    public String apiBaseUrl() {
        return "http://" + host + ":" + apiPort;
    }

    /**
     * Parse connection options from the raw argument list, falling back to env vars then defaults.
     */
    public static Conn from(String[] args) {
        String host = envOr("CLOUDSTUB_HOST", "localhost");
        int apiPort = parsePort(envOr("CLOUDSTUB_API_PORT", "4567"), 4567);

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            host = valueOf(a, "--host", args, i, host, s -> s);
            apiPort = valueOf(a, "--api-port", args, i, apiPort, s -> parsePort(s, 4567));
        }
        return new Conn(host, apiPort);
    }

    private interface Conv<T> {
        T apply(String s);
    }

    private static <T> T valueOf(
            String arg, String flag, String[] args, int i, T current, Conv<T> conv) {
        if (arg.equals(flag) && i + 1 < args.length) {
            return conv.apply(args[i + 1]);
        }
        if (arg.startsWith(flag + "=")) {
            return conv.apply(arg.substring(flag.length() + 1));
        }
        return current;
    }

    private static int parsePort(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String envOr(String key, String fallback) {
        String v = System.getenv(key);
        return v != null && !v.isBlank() ? v : fallback;
    }
}
