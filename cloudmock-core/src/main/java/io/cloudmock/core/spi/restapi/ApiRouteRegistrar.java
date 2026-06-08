package io.cloudmock.core.spi.restapi;

import io.cloudmock.core.spi.CloudMockApiService;
import io.cloudmock.core.spi.HttpMethod;

import java.util.List;

/**
 * Passed to {@link CloudMockApiService#registerRoutes} so modules can declare API routes
 * without depending on any HTTP transport type.
 */
public interface ApiRouteRegistrar {

    /**
     * Register a route under {@code /api/<serviceId>/<path>}, advertising a CLI command name
     * and its parameters so a generic client can drive it.
     *
     * @param method      HTTP method (GET, POST, …)
     * @param path        path relative to {@code /api/<serviceId>}, e.g. {@code "/messages"}
     * @param command     CLI command name for {@code <service> <command>}, e.g. {@code "send-message"}
     * @param description one-line description, surfaced in {@code /api/status} and OpenAPI
     * @param params      parameters the route accepts as query-string values; may be empty
     * @param handler     invoked on every matching request
     */
    void register(HttpMethod method, String path, String command, String description,
                  List<ApiParam> params, ApiHandler handler);

    /**
     * Convenience overload for a route with no CLI command name and no parameters. The command
     * name defaults to the path with the leading slash removed.
     */
    default void register(HttpMethod method, String path, String description, ApiHandler handler) {
        String command = path.startsWith("/") ? path.substring(1) : path;
        register(method, path, command, description, List.of(), handler);
    }
}
