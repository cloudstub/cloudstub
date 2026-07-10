package io.cloudstub.ssm;

import io.cloudstub.core.spi.Json;
import io.cloudstub.core.spi.StateStore;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SSM Parameter Store value helpers: ARN construction, type normalization, and building the JSON
 * response shapes from a stored parameter map. Dependency-free (JDK plus the core SPI).
 *
 * <p>A stored parameter is a {@code Map<String, String>} with the fields {@code name}, {@code
 * value}, {@code type}, {@code version}, {@code dataType}, {@code description}, and {@code
 * lastModifiedDate} (epoch seconds as a string).
 */
final class SsmParameters {

    private SsmParameters() {}

    static final String ARN_PREFIX = "arn:aws:ssm:us-east-1:000000000000:parameter";

    /** The three SSM parameter types; anything else defaults to {@code String}. */
    private static final List<String> TYPES = List.of("String", "StringList", "SecureString");

    static final String DEFAULT_TYPE = "String";
    static final String DEFAULT_DATA_TYPE = "text";

    /**
     * Builds the stored map for a new parameter version. The version is 1 when {@code existing} is
     * {@code null}, else one past the existing version, so a repeated write advances the version.
     * The map shape is defined here alone, so the AWS-protocol and REST surfaces cannot drift.
     *
     * <p>On an overwrite (when {@code existing} is non-null), {@code type} and {@code dataType} are
     * inherited from the existing parameter when the request omits them, matching AWS: an overwrite
     * that does not restate the type does not silently downgrade it (e.g. a {@code SecureString}
     * stays a {@code SecureString}).
     */
    static Map<String, String> newVersion(
            Map<String, String> existing,
            String name,
            String value,
            String type,
            String dataType,
            String description) {
        long version = existing != null ? version(existing) + 1 : 1;
        Map<String, String> parameter = new LinkedHashMap<>();
        parameter.put("name", name);
        parameter.put("value", nullToEmpty(value));
        parameter.put("type", resolveType(type, existing));
        parameter.put("version", String.valueOf(version));
        parameter.put("dataType", resolveDataType(dataType, existing));
        parameter.put("description", nullToEmpty(description));
        parameter.put("lastModifiedDate", String.valueOf(Instant.now().getEpochSecond()));
        return parameter;
    }

    private static String resolveType(String requested, Map<String, String> existing) {
        if (requested != null) {
            return normalizeType(requested);
        }
        if (existing != null && existing.get("type") != null) {
            return existing.get("type");
        }
        return DEFAULT_TYPE;
    }

    private static String resolveDataType(String requested, Map<String, String> existing) {
        if (requested != null) {
            return requested;
        }
        if (existing != null && existing.get("dataType") != null) {
            return existing.get("dataType");
        }
        return DEFAULT_DATA_TYPE;
    }

    /** The stored parameter/tag map for {@code key}, or {@code null} if absent or not a map. */
    @SuppressWarnings("unchecked")
    static Map<String, String> read(StateStore store, String key) {
        Object value = store.get(key);
        return value instanceof Map ? (Map<String, String>) value : null;
    }

    /** The ARN for a parameter, e.g. {@code ...:parameter/prod/db} for name {@code /prod/db}. */
    static String arn(String name) {
        return ARN_PREFIX + (name.startsWith("/") ? name : "/" + name);
    }

    /** The requested type if it is one of the three valid values, else {@code String}. */
    static String normalizeType(String type) {
        return type != null && TYPES.contains(type) ? type : DEFAULT_TYPE;
    }

    /** The stored {@code version} as a number, or {@code 0} if missing or non-numeric. */
    static long version(Map<String, String> parameter) {
        return parseLong(parameter.get("version"));
    }

    /** The stored {@code lastModifiedDate} (epoch seconds) as a number, or {@code 0}. */
    static long lastModified(Map<String, String> parameter) {
        return parseLong(parameter.get("lastModifiedDate"));
    }

    /** The {@code GetParameter}/{@code GetParameters}/{@code GetParametersByPath} shape. */
    static Map<String, Object> parameterShape(Map<String, String> parameter) {
        String name = parameter.get("name");
        return Json.object(
                "Name", name,
                "Type", parameter.get("type"),
                "Value", parameter.get("value"),
                "Version", version(parameter),
                "LastModifiedDate", lastModified(parameter),
                "ARN", arn(name),
                "DataType", parameter.get("dataType"));
    }

    /** The {@code DescribeParameters} metadata shape (no value). */
    static Map<String, Object> metadataShape(Map<String, String> parameter) {
        String name = parameter.get("name");
        return Json.object(
                "Name", name,
                "ARN", arn(name),
                "Type", parameter.get("type"),
                "Version", version(parameter),
                "LastModifiedDate", lastModified(parameter),
                "DataType", parameter.get("dataType"),
                "Description", nullToEmpty(parameter.get("description")),
                "Tier", "Standard");
    }

    /** The {@code GetParameterHistory} entry shape for the current version. */
    static Map<String, Object> historyShape(Map<String, String> parameter) {
        String name = parameter.get("name");
        return Json.object(
                "Name", name,
                "Type", parameter.get("type"),
                "Value", parameter.get("value"),
                "Version", version(parameter),
                "LastModifiedDate", lastModified(parameter),
                "Description", nullToEmpty(parameter.get("description")),
                "Labels", List.of(),
                "Tier", "Standard",
                "DataType", parameter.get("dataType"));
    }

    static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** Parses a long, returning {@code 0} on a missing or non-numeric value. */
    static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
