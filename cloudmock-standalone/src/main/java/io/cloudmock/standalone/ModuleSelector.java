package io.cloudmock.standalone;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Resolves which service modules the standalone server should enable.
 *
 * <p>Precedence: {@code --modules=<a,b>} CLI flag, then {@code CLOUDMOCK_MODULES} environment
 * variable. When neither is set (or the value is blank), {@code null} is returned, meaning
 * "enable every module discovered on the classpath".
 */
final class ModuleSelector {

    private ModuleSelector() {}

    /** @return the requested module IDs, or {@code null} to enable all discovered modules. */
    static Set<String> resolve(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--modules=")) {
                return parse(args[i].substring("--modules=".length()));
            }
            if ("--modules".equals(args[i]) && i + 1 < args.length) {
                return parse(args[i + 1]);
            }
        }
        String env = System.getenv("CLOUDMOCK_MODULES");
        if (env != null && !env.isBlank()) {
            return parse(env);
        }
        return null;
    }

    private static Set<String> parse(String csv) {
        Set<String> ids = new LinkedHashSet<>();
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                ids.add(trimmed);
            }
        }
        return ids.isEmpty() ? null : ids;
    }
}
