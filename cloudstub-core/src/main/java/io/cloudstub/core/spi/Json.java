package io.cloudstub.core.spi;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Helpers for assembling JSON response bodies from plain JDK collections, to pass to {@link
 * StubResponse#json(Map)}. JDK-only; exposes no serialisation type.
 */
public final class Json {

    private Json() {}

    /**
     * An ordered JSON object built from alternating key/value arguments, e.g. {@code object("Name",
     * name, "VersionId", id)}. Keys must be strings; values must be JSON-serialisable JDK types
     * (including nested {@code Map}/{@code List} produced by this method). Insertion order is
     * preserved.
     *
     * @throws IllegalArgumentException if an odd number of arguments is given
     */
    public static Map<String, Object> object(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException(
                    "Json.object requires an even number of key/value arguments");
        }
        Map<String, Object> object = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            object.put((String) keyValues[i], keyValues[i + 1]);
        }
        return object;
    }
}
