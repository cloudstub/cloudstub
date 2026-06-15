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
 * Optional {@code .properties} configuration file for standalone mode.
 *
 * <p>The file sits between environment variables and built-in defaults in the resolution
 * precedence: a CLI flag overrides an environment variable, which overrides a config-file value,
 * which overrides the built-in default. Each resolver consults {@link #get(String)} after its
 * environment variable and before its default, so the file changes nothing about how flags and
 * environment variables already behave.
 *
 * <p>The file is located from {@code --config=<path>}, then the {@code CLOUDSTUB_CONFIG}
 * environment variable, then {@code ./cloudstub.properties} in the working directory. An explicit
 * {@code --config} (or {@code CLOUDSTUB_CONFIG}) path that does not exist is rejected; a missing
 * default file is not an error and yields an empty configuration, so the server starts on defaults
 * exactly as it does with no file. A file that cannot be parsed, that contains a key outside {@link
 * #KNOWN_KEYS the documented set}, or that holds a non-numeric value for a numeric key, throws
 * {@link LocalConfigException} with a message naming the file and the offending key — the launcher
 * turns that into a fast exit, not a stack trace.
 *
 * <p>Keys are namespaced under {@code cloudstub.} with room to grow (e.g. future {@code
 * cloudstub.faults.*}). Values mirror the CLI flags: {@link #KEY_SERVICES} is the same
 * comma-separated list as {@code --services}, {@link #KEY_AUTO_DOWNLOAD} the same boolean as {@code
 * --no-download} inverts, and so on.
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

    /** An empty configuration backed by no file — every {@link #get(String)} returns absent. */
    public static LocalConfig empty() {
        return new LocalConfig(new Properties(), "(none)");
    }

    /**
     * Loads the config file resolved from {@code --config}, the {@code CLOUDSTUB_CONFIG}
     * environment variable, or the default {@code ./cloudstub.properties}.
     *
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
     * @return the trimmed value for {@code key}, or absent when the key is missing or blank.
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
     * @return the value for {@code key} parsed as an integer, or absent when the key is missing or
     *     blank. Fails fast naming the file and key when the value is not a valid integer.
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
