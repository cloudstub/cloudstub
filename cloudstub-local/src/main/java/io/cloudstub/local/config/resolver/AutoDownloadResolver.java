package io.cloudstub.local.config.resolver;

import io.cloudstub.local.config.LocalConfig;
import java.util.Optional;

/**
 * Resolves whether absent service module jars are auto-downloaded from Maven Central.
 *
 * <p>Auto-download is <strong>on by default</strong> so that {@code --services} is the single
 * source of truth: declare a service and the launcher provisions it. It is turned off with the
 * {@code --no-download} CLI flag, by setting {@code CLOUDSTUB_AUTO_DOWNLOAD} to a falsy value
 * ({@code false}/{@code 0}/{@code no}/{@code off}), or by setting the {@code
 * cloudstub.auto-download} config-file key to a falsy value, which makes a fully offline /
 * air-gapped run possible: a declared-but-missing service then fails fast rather than reaching the
 * network.
 */
public final class AutoDownloadResolver {

    private AutoDownloadResolver() {}

    static boolean isEnabled(String[] args) {
        return isEnabled(args, LocalConfig.empty());
    }

    public static boolean isEnabled(String[] args, LocalConfig config) {
        for (String arg : args) {
            if ("--no-download".equals(arg)) {
                return false;
            }
        }
        String env = System.getenv("CLOUDSTUB_AUTO_DOWNLOAD");
        if (env != null && !env.isBlank()) {
            return !isFalsy(env.trim());
        }
        Optional<String> configured = config.get(LocalConfig.KEY_AUTO_DOWNLOAD);
        if (configured.isPresent()) {
            return !isFalsy(configured.get());
        }
        return true;
    }

    private static boolean isFalsy(String value) {
        return value.equalsIgnoreCase("false")
                || value.equals("0")
                || value.equalsIgnoreCase("no")
                || value.equalsIgnoreCase("off");
    }
}
