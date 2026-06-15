package io.cloudstub.local.config;

import io.cloudstub.local.config.exception.LocalConfigException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Properties;
import java.util.Set;

/**
 * Optional {@code .properties} configuration file for standalone mode, located from {@code
 * --config=<path>}, then {@code CLOUDSTUB_CONFIG}, then {@code ./cloudstub.properties}. Consulted
 * by each resolver after its environment variable and before its default.
 */
public final class LocalConfig {

    public static final String KEY_PORT = "cloudstub.port";
    public static final String KEY_API_PORT = "cloudstub.api-port";
    public static final String KEY_SERVICES = "cloudstub.services";
    public static final String KEY_STORE_DIR = "cloudstub.store-dir";
    public static final String KEY_MAX_HISTORY = "cloudstub.max-history";
    public static final String KEY_MODULES_DIR = "cloudstub.modules-dir";
    public static final String KEY_MODULE_VERSION = "cloudstub.module-version";
    public static final String KEY_MAVEN_BASE_URL = "cloudstub.maven-base-url";
    public static final String KEY_AUTO_DOWNLOAD = "cloudstub.auto-download";

    static final Set<String> KNOWN_KEYS =
            Set.of(
                    KEY_PORT,
                    KEY_API_PORT,
                    KEY_SERVICES,
                    KEY_STORE_DIR,
                    KEY_MAX_HISTORY,
                    KEY_MODULES_DIR,
                    KEY_MODULE_VERSION,
                    KEY_MAVEN_BASE_URL,
                    KEY_AUTO_DOWNLOAD);

    static final String DEFAULT_CONFIG_FILE = "cloudstub.properties";

    private final Properties properties;
    private final String source;

    private LocalConfig(Properties properties, String source) {
        this.properties = properties;
        this.source = source;
    }

    /** An empty configuration backed by no file. */
    public static LocalConfig empty() {
        return new LocalConfig(new Properties(), "(none)");
    }

    /**
     * @throws LocalConfigException on an explicit path that does not exist, an unparseable file, or
     *     an unknown key
     */
    public static LocalConfig load(String[] args) {
        boolean explicitlyRequested = false;
        String location = flagValue(args);
        if (location == null || location.isBlank()) {
            String env = System.getenv("CLOUDSTUB_CONFIG");
            if (env != null && !env.isBlank()) {
                location = env;
            }
        }
        if (location != null && !location.isBlank()) {
            explicitlyRequested = true;
        } else {
            location = DEFAULT_CONFIG_FILE;
        }

        Path path = Path.of(location.trim());
        if (!Files.exists(path)) {
            if (explicitlyRequested) {
                throw new LocalConfigException("config file '" + path + "' does not exist.");
            }
            return empty();
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        } catch (IllegalArgumentException | IOException e) {
            throw new LocalConfigException(
                    "failed to read config file '" + path + "': " + e.getMessage());
        }

        List<String> unknown =
                props.stringPropertyNames().stream()
                        .filter(k -> !KNOWN_KEYS.contains(k))
                        .sorted()
                        .toList();
        if (!unknown.isEmpty()) {
            throw new LocalConfigException(
                    "unknown key(s) in config file '"
                            + path
                            + "': "
                            + String.join(", ", unknown)
                            + ". Known keys: "
                            + knownKeys());
        }
        return new LocalConfig(props, path.toString());
    }

    /**
     * @return the trimmed value for {@code key}, or absent when missing or blank.
     */
    public Optional<String> get(String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? Optional.empty() : Optional.of(trimmed);
    }

    /**
     * @return the value for {@code key} as an int, or absent when missing or blank.
     */
    public OptionalInt getInt(String key) {
        Optional<String> value = get(key);
        if (value.isEmpty()) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(value.get()));
        } catch (NumberFormatException e) {
            throw new LocalConfigException(
                    "invalid value for '"
                            + key
                            + "' in config file '"
                            + source
                            + "': '"
                            + value.get()
                            + "' is not an integer.");
        }
    }

    private static String flagValue(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--config=")) {
                return args[i].substring("--config=".length());
            }
            if ("--config".equals(args[i]) && i + 1 < args.length) {
                return args[i + 1];
            }
        }
        return null;
    }

    private static String knownKeys() {
        return KNOWN_KEYS.stream().sorted().reduce((a, b) -> a + ", " + b).orElse("");
    }
}
