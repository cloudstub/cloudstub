package io.cloudmock.cli.util;

import com.fasterxml.jackson.databind.JsonNode;
import io.cloudmock.cli.http.ApiClient;

/** Terminal output helpers. No ANSI colour, so output stays clean in pipes and scripts. */
public final class Printer {

    private Printer() {}

    public static void kv(String key, String value) {
        System.out.printf("%-14s %s%n", key + ":", value);
    }

    public static void header(String text) {
        System.out.println(text);
        System.out.println("-".repeat(text.length()));
    }

    public static void json(JsonNode node) {
        System.out.println(node.toPrettyString());
    }

    /** Render an API call result: pretty JSON on success, the server's error message otherwise. */
    public static void result(ApiClient.Result res) {
        if (res.isSuccess()) {
            json(res.body());
        } else {
            JsonNode error = res.body().get("error");
            String message = error != null ? error.asText() : res.body().toString();
            System.err.println("Error (HTTP " + res.statusCode() + "): " + message);
        }
    }

    public static void unavailable(String baseUrl) {
        System.err.println("CloudMock is not running at " + baseUrl + ".");
        System.err.println("Start it with: java -jar cloudmock-standalone.jar");
    }
}
