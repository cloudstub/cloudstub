package io.cloudstub.local;

/**
 * Resolves whether absent service module jars are auto-downloaded from Maven Central.
 *
 * <p>Auto-download is <strong>on by default</strong> so that {@code --services} is the single
 * source of truth: declare a service and the launcher provisions it. It is turned off with the
 * {@code --no-download} CLI flag or by setting {@code CLOUDSTUB_AUTO_DOWNLOAD} to a falsy value
 * ({@code false}/{@code 0}/{@code no}/{@code off}), which makes a fully offline / air-gapped run
 * possible: a declared-but-missing service then fails fast rather than reaching the network.
 */
final class AutoDownloadResolver {

    private AutoDownloadResolver() {}

    static boolean isEnabled(String[] args) {
        for (String arg : args) {
            if ("--no-download".equals(arg)) {
                return false;
            }
        }
        String env = System.getenv("CLOUDSTUB_AUTO_DOWNLOAD");
        if (env != null && !env.isBlank()) {
            return !isFalsy(env.trim());
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
