package io.cloudstub.sns;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Parses an {@code application/x-www-form-urlencoded} request body into its parameters. */
final class SnsForm {

    private SnsForm() {}

    /**
     * Parses {@code body} into a parameter map. Malformed pairs are skipped; a {@code null} or
     * empty body yields an empty map. Both names and values are URL-decoded.
     */
    static Map<String, String> parse(String body) {
        Map<String, String> params = new LinkedHashMap<>();
        if (body == null || body.isEmpty()) {
            return params;
        }
        for (String pair : body.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            if (eq < 0) {
                params.put(decode(pair), "");
            } else {
                params.put(decode(pair.substring(0, eq)), decode(pair.substring(eq + 1)));
            }
        }
        return params;
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}
