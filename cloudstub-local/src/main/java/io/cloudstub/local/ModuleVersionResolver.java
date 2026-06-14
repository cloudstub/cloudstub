package io.cloudstub.local;

import io.cloudstub.core.download.CoreVersion;

/**
 * Resolves the version of the module jars to auto-download.
 *
 * <p>Precedence: {@code --module-version=<v>} CLI flag, then {@code CLOUDSTUB_MODULE_VERSION}
 * environment variable, then the running {@code cloudstub-core} version. Defaulting to the running
 * core version keeps a downloaded module and the core providing its SPI on the same release; the
 * override is for advanced use.
 */
final class ModuleVersionResolver {

    private ModuleVersionResolver() {}

    /**
     * @return the version to download; never blank
     */
    static String resolve(String[] args) {
        String explicit = flagValue(args);
        if (explicit != null && !explicit.isBlank()) {
            return explicit.trim();
        }
        String env = System.getenv("CLOUDSTUB_MODULE_VERSION");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return CoreVersion.current();
    }

    private static String flagValue(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--module-version=")) {
                return args[i].substring("--module-version=".length());
            }
            if ("--module-version".equals(args[i]) && i + 1 < args.length) {
                return args[i + 1];
            }
        }
        return null;
    }
}
