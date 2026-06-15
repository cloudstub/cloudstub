package io.cloudstub.local.config.resolver;

import io.cloudstub.core.download.ModuleDownloader;
import io.cloudstub.local.config.LocalConfig;

/**
 * Resolves the Maven repository base URL that auto-download fetches module jars from.
 *
 * <p>Precedence: {@code --maven-base-url=<url>} CLI flag, then {@code CLOUDSTUB_MAVEN_BASE_URL}
 * environment variable, then the {@code cloudstub.maven-base-url} config-file key, then {@link
 * ModuleDownloader#CENTRAL_BASE_URL Maven Central}. The override exists so an air-gapped run can
 * point at an internal mirror of Central; it is not a general multi-repository resolver — a single
 * base URL in Maven layout.
 */
public final class MavenBaseUrlResolver {

    private MavenBaseUrlResolver() {}

    static String resolve(String[] args) {
        return resolve(args, LocalConfig.empty());
    }

    public static String resolve(String[] args, LocalConfig config) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--maven-base-url=")) {
                String value = args[i].substring("--maven-base-url=".length());
                if (!value.isBlank()) {
                    return value.trim();
                }
            }
            if ("--maven-base-url".equals(args[i]) && i + 1 < args.length) {
                String value = args[i + 1];
                if (!value.isBlank()) {
                    return value.trim();
                }
            }
        }
        String env = System.getenv("CLOUDSTUB_MAVEN_BASE_URL");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return config.get(LocalConfig.KEY_MAVEN_BASE_URL).orElse(ModuleDownloader.CENTRAL_BASE_URL);
    }
}
