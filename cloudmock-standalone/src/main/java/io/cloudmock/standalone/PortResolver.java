package io.cloudmock.standalone;

final class PortResolver {

    static final int DEFAULT_PORT = 4566;

    private PortResolver() {}

    static int resolve(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--port=")) {
                return Integer.parseInt(args[i].substring("--port=".length()));
            }
            if ("--port".equals(args[i]) && i + 1 < args.length) {
                return Integer.parseInt(args[i + 1]);
            }
        }
        String envPort = System.getenv("CLOUDMOCK_PORT");
        if (envPort != null && !envPort.isBlank()) {
            return Integer.parseInt(envPort);
        }
        return DEFAULT_PORT;
    }
}
