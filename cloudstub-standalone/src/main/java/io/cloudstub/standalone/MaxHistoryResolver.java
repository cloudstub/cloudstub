package io.cloudstub.standalone;

import io.cloudstub.core.CloudStub;

/**
 * Resolves the cap on retained request-history entries for the long-lived standalone process.
 *
 * <p>Precedence: {@code --max-history=<n>} CLI flag, then {@code CLOUDSTUB_MAX_HISTORY} environment
 * variable, then {@link CloudStub#DEFAULT_MAX_REQUEST_HISTORY}. A value of {@code 0} (or {@code
 * unlimited} / {@code none}) retains an unbounded history.
 */
final class MaxHistoryResolver {

    private MaxHistoryResolver() {}

    static int resolve(String[] args) {
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
