package io.cloudmock.cli.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Generic HTTP client for the CloudMock REST API. Knows only how to call routes and read JSON —
 * it has no knowledge of any specific service.
 */
public class ApiClient {

    /** Result of an API call: the HTTP status and the parsed JSON body (never null). */
    public record Result(int statusCode, JsonNode body) {
        public boolean isSuccess() {
            return statusCode / 100 == 2;
        }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final String baseUrl;

    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String baseUrl() {
        return baseUrl;
    }

    /** GET /api/status, returning the parsed body. Throws if the server is unreachable. */
    public JsonNode getStatus() throws CloudMockUnavailableException {
        return call("GET", "/api/status", Map.of()).body();
    }

    /** Call an arbitrary API route. Connection failures throw; HTTP error statuses do not. */
    public Result call(String method, String path, Map<String, String> queryParams)
            throws CloudMockUnavailableException {
        String url = baseUrl + path + queryString(queryParams);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .method(method, HttpRequest.BodyPublishers.noBody());
        try {
            HttpResponse<String> resp = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new Result(resp.statusCode(), parse(resp.body()));
        } catch (ConnectException | HttpTimeoutException e) {
            throw new CloudMockUnavailableException(baseUrl);
        } catch (IOException | InterruptedException e) {
            // A mid-flight transport failure is, for a CLI, indistinguishable from "not running".
            throw new CloudMockUnavailableException(baseUrl, e);
        }
    }

    /** Parse a response body as JSON, or wrap non-JSON text so callers always get a node. */
    private static JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return MAPPER.createObjectNode();
        }
        try {
            return MAPPER.readTree(body);
        } catch (IOException e) {
            return new TextNode(body);
        }
    }

    private static String queryString(Map<String, String> params) {
        if (params.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner("&", "?", "");
        params.forEach((k, v) -> joiner.add(encode(k) + "=" + encode(v)));
        return joiner.toString();
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
