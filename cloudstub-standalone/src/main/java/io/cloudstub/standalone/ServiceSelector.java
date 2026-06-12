package io.cloudstub.standalone;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Resolves which services the standalone server should enable.
 *
 * <p>Precedence: {@code --services=<a,b>} CLI flag, then {@code CLOUDSTUB_SERVICES} environment
 * variable. When neither is set (or the value is blank), {@code null} is returned, meaning "no
 * selection was made" — the launcher treats that as "enable nothing", so services load only when
 * explicitly declared.
 */
final class ServiceSelector {

    private ServiceSelector() {}

    /**
     * @return the requested service IDs, or {@code null} when no selection was made.
     */
    static Set<String> resolve(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--services=")) {
                return parse(args[i].substring("--services=".length()));
            }
            if ("--services".equals(args[i]) && i + 1 < args.length) {
                return parse(args[i + 1]);
            }
        }
        String env = System.getenv("CLOUDSTUB_SERVICES");
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
